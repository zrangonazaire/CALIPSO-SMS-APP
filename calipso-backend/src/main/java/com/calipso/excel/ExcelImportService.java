package com.calipso.excel;

import com.calipso.campaign.Campaign;
import com.calipso.campaign.CampaignChannel;
import com.calipso.campaign.CampaignRepository;
import com.calipso.campaign.CampaignStatus;
import com.calipso.campaign.FileAnalysisResponse;
import com.calipso.excelvariable.ExcelVariable;
import com.calipso.excelvariable.ExcelVariableRepository;
import com.calipso.recipient.CampaignRecipient;
import com.calipso.recipient.CampaignRecipientRepository;
import com.calipso.recipient.RecipientStatus;
import com.calipso.sms.MultichannelGatewayService;
import com.calipso.sms.SmsMessageService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ExcelImportService {

    private final CampaignRepository campaignRepository;
    private final ExcelVariableRepository variableRepository;
    private final CampaignRecipientRepository recipientRepository;
    private final SmsMessageService smsMessageService;
    private final MultichannelGatewayService gatewayService;

    public FileAnalysisResponse analyzeFile(MultipartFile file) {
        String filename = file.getOriginalFilename() == null ? "contacts.xlsx" : file.getOriginalFilename();
        List<String> headers;
        List<Map<String, Object>> allRows;

        if (filename.toLowerCase().endsWith(".csv")) {
            ParsedTable parsed = parseCsv(file);
            headers = parsed.headers();
            allRows = parsed.rows();
        } else {
            ParsedTable parsed = parseWorkbook(file);
            headers = parsed.headers();
            allRows = parsed.rows();
        }

        String detectedPhone = detectPhoneColumn(headers, allRows);
        String detectedEmail = detectEmailColumn(headers, allRows);
        String detectedName = detectNameColumn(headers);

        List<Map<String, Object>> sampleRows = allRows.stream().limit(10).toList();
        return new FileAnalysisResponse(
                filename,
                headers,
                detectedPhone,
                detectedEmail,
                detectedName,
                allRows.size(),
                sampleRows,
                allRows
        );
    }

    @Transactional
    public Campaign importExcel(Long campaignId, MultipartFile file) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("Campagne introuvable"));

        FileAnalysisResponse analysis = analyzeFile(file);

        if (campaign.getProfile() != null) {
            List<ExcelVariable> variables = variableRepository.findByProfileIdAndActiveTrue(
                    campaign.getProfile().getId()
            );
            if (!variables.isEmpty()) {
                validateRequiredHeaders(analysis.headers(), variables);
            }
        }

        String targetCol = resolveTargetColumn(campaign, analysis);
        return importRowsIntoCampaign(campaign, analysis.allRows(), targetCol);
    }

    @Transactional
    public Campaign importRowsIntoCampaign(
            Campaign campaign,
            List<Map<String, Object>> rows,
            String explicitRecipientColumn
    ) {
        List<CampaignRecipient> existing = recipientRepository.findByCampaignId(campaign.getId());
        if (!existing.isEmpty() && campaign.getStatus() != CampaignStatus.COMPLETED) {
            recipientRepository.deleteAll(existing);
        }

        CampaignChannel channel = campaign.resolvedChannel();
        String templateContent = campaign.resolvedTemplateContent();

        List<String> headers = rows == null || rows.isEmpty()
                ? List.of()
                : new ArrayList<>(rows.getFirst().keySet());

        String targetColumn = explicitRecipientColumn;
        if (targetColumn == null || targetColumn.isBlank()) {
            if (campaign.getRecipientColumn() != null && !campaign.getRecipientColumn().isBlank()) {
                targetColumn = campaign.getRecipientColumn();
            } else if (campaign.getPhoneVariable() != null) {
                targetColumn = campaign.getPhoneVariable().getCode();
            } else if (channel == CampaignChannel.EMAIL) {
                targetColumn = detectEmailColumn(headers, rows == null ? List.of() : rows);
            } else {
                targetColumn = detectPhoneColumn(headers, rows == null ? List.of() : rows);
            }
        }

        String nameColumn = detectNameColumn(headers);
        String emailColumn = detectEmailColumn(headers, rows == null ? List.of() : rows);

        int total = 0;
        int valid = 0;
        int invalid = 0;
        int totalSegments = 0;

        if (rows != null) {
            for (Map<String, Object> rawData : rows) {
                if (rawData == null || rawData.isEmpty()) {
                    continue;
                }
                total++;

                String rawTarget = extractColumnValue(rawData, targetColumn);
                String normalizedTarget = gatewayService.normalizeTarget(channel, rawTarget);
                boolean targetValid = gatewayService.isValidTarget(channel, rawTarget);

                CampaignRecipient recipient = new CampaignRecipient();
                recipient.setCampaign(campaign);
                recipient.setChannel(channel);
                recipient.setRawData(rawData);
                recipient.setPhoneNumber(normalizedTarget);
                recipient.setEmail(channel == CampaignChannel.EMAIL ? normalizedTarget : extractColumnValue(rawData, emailColumn));
                recipient.setRecipientName(extractColumnValue(rawData, nameColumn));

                if (!targetValid) {
                    recipient.setStatus(RecipientStatus.INVALID);
                    recipient.setErrorMessage(channel == CampaignChannel.EMAIL
                            ? "Adresse email manquante ou invalide"
                            : "Numéro de téléphone manquant ou invalide");
                    recipient.setSegmentCount(0);
                    invalid++;
                } else {
                    String message = smsMessageService.generateMessage(templateContent, rawData);
                    int units = gatewayService.calculateUnits(channel, message, smsMessageService);

                    recipient.setGeneratedMessage(message);
                    recipient.setSegmentCount(units);
                    recipient.setStatus(RecipientStatus.VALID);

                    valid++;
                    totalSegments += units;
                }

                recipientRepository.save(recipient);
            }
        }

        campaign.setRecipientColumn(targetColumn);
        campaign.setTotalRecipients(total);
        campaign.setTotalValid(valid);
        campaign.setTotalInvalid(invalid);
        campaign.setTotalSegments(totalSegments);
        campaign.setStatus(CampaignStatus.IMPORTED);

        return campaignRepository.save(campaign);
    }

    public byte[] generateSampleExcel() {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Destinataires");
            String[] columns = {"TELEPHONE", "PRENOM", "NOM", "EMAIL", "MONTANT", "VILLE"};

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            String[][] sampleData = {
                    {"+2250708091011", "Awa", "Kouassi", "awa.kouassi@exemple.ci", "25 000 FCFA", "Abidjan"},
                    {"+2250504030201", "Marc", "Konan", "marc.konan@exemple.ci", "18 500 FCFA", "Bouaké"},
                    {"+2250102030405", "Fatou", "Diallo", "fatou.diallo@exemple.ci", "42 000 FCFA", "Yamoussoukro"},
                    {"+2250701020304", "Jean-Luc", "Bamba", "jl.bamba@exemple.ci", "15 000 FCFA", "San-Pédro"}
            };

            for (int r = 0; r < sampleData.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < sampleData[r].length; c++) {
                    row.createCell(c).setCellValue(sampleData[r][c]);
                }
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Impossible de générer le modèle Excel", e);
        }
    }

    private String resolveTargetColumn(Campaign campaign, FileAnalysisResponse analysis) {
        if (campaign.getRecipientColumn() != null && !campaign.getRecipientColumn().isBlank()) {
            return campaign.getRecipientColumn();
        }
        if (campaign.getPhoneVariable() != null && campaign.getPhoneVariable().getCode() != null) {
            return campaign.getPhoneVariable().getCode();
        }
        if (campaign.resolvedChannel() == CampaignChannel.EMAIL) {
            return analysis.detectedEmailColumn() != null ? analysis.detectedEmailColumn() : analysis.detectedPhoneColumn();
        }
        return analysis.detectedPhoneColumn() != null ? analysis.detectedPhoneColumn() : (analysis.headers().isEmpty() ? "" : analysis.headers().getFirst());
    }

    private String extractColumnValue(Map<String, Object> rawData, String column) {
        if (rawData == null || column == null || column.isBlank()) {
            return null;
        }
        if (rawData.containsKey(column)) {
            return valueAsString(rawData.get(column));
        }
        for (Map.Entry<String, Object> entry : rawData.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(column)) {
                return valueAsString(entry.getValue());
            }
        }
        return null;
    }

    private ParsedTable parseWorkbook(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new RuntimeException("Le fichier Excel ne contient pas d'entêtes à la première ligne.");
            }

            List<String> headers = readHeaders(headerRow);
            List<Map<String, Object>> rows = new ArrayList<>();

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null || isEmptyRow(row)) {
                    continue;
                }
                rows.add(readRowData(row, headers));
            }

            return new ParsedTable(headers, rows);
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la lecture du fichier Excel : " + e.getMessage(), e);
        }
    }

    private ParsedTable parseCsv(MultipartFile file) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new RuntimeException("Le fichier CSV est vide.");
            }
            // Strip BOM if present
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }

            String delimiter = headerLine.contains(";") ? ";" : ",";
            String[] rawHeaders = headerLine.split(delimiter, -1);
            List<String> headers = new ArrayList<>();
            for (String h : rawHeaders) {
                String clean = normalizeHeader(h.replace("\"", ""));
                if (!clean.isBlank()) {
                    headers.add(clean);
                }
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split(delimiter, -1);
                Map<String, Object> rowMap = new LinkedHashMap<>();
                boolean hasContent = false;
                for (int i = 0; i < headers.size(); i++) {
                    String val = i < parts.length ? parts[i].replace("\"", "").trim() : "";
                    if (!val.isBlank()) {
                        hasContent = true;
                    }
                    rowMap.put(headers.get(i), val);
                }
                if (hasContent) {
                    rows.add(rowMap);
                }
            }

            return new ParsedTable(headers, rows);
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de la lecture du fichier CSV : " + e.getMessage(), e);
        }
    }

    private String detectPhoneColumn(List<String> headers, List<Map<String, Object>> rows) {
        List<String> phoneKeywords = List.of("TELEPHONE", "PHONE", "TEL", "MOBILE", "NUMERO", "WHATSAPP", "CONTACT", "GSM", "CELL");
        for (String kw : phoneKeywords) {
            for (String h : headers) {
                if (h.equals(kw) || h.contains(kw)) {
                    return h;
                }
            }
        }
        // Fallback: inspect values of first row
        if (!rows.isEmpty()) {
            Map<String, Object> first = rows.getFirst();
            for (String h : headers) {
                String val = valueAsString(first.get(h));
                if (val != null && val.replaceAll("\\D", "").length() >= 8) {
                    return h;
                }
            }
        }
        return headers.isEmpty() ? null : headers.getFirst();
    }

    private String detectEmailColumn(List<String> headers, List<Map<String, Object>> rows) {
        List<String> emailKeywords = List.of("EMAIL", "MAIL", "COURRIEL", "E_MAIL");
        for (String kw : emailKeywords) {
            for (String h : headers) {
                if (h.equals(kw) || h.contains(kw)) {
                    return h;
                }
            }
        }
        if (!rows.isEmpty()) {
            Map<String, Object> first = rows.getFirst();
            for (String h : headers) {
                String val = valueAsString(first.get(h));
                if (val != null && val.contains("@")) {
                    return h;
                }
            }
        }
        return null;
    }

    private String detectNameColumn(List<String> headers) {
        List<String> nameKeywords = List.of("PRENOM", "NOM", "CLIENT", "NOM_COMPLET", "FULLNAME", "NAME", "DESTINATAIRE");
        for (String kw : nameKeywords) {
            for (String h : headers) {
                if (h.equals(kw) || h.contains(kw)) {
                    return h;
                }
            }
        }
        return null;
    }

    private List<String> readHeaders(Row headerRow) {
        List<String> headers = new ArrayList<>();

        for (Cell cell : headerRow) {
            Object raw = readCellValue(cell);
            String value = raw == null ? "" : raw.toString();

            if (!value.isBlank()) {
                headers.add(normalizeHeader(value));
            }
        }

        return headers;
    }

    private void validateRequiredHeaders(List<String> headers, List<ExcelVariable> variables) {
        for (ExcelVariable variable : variables) {
            if (Boolean.TRUE.equals(variable.getRequired())) {
                if (!headers.contains(variable.getCode())) {
                    throw new RuntimeException(
                            "Colonne obligatoire absente : " + variable.getCode()
                    );
                }
            }
        }
    }

    private Map<String, Object> readRowData(Row row, List<String> headers) {
        Map<String, Object> data = new LinkedHashMap<>();

        for (int i = 0; i < headers.size(); i++) {
            Cell cell = row.getCell(i);
            data.put(headers.get(i), readCellValue(cell));
        }

        return data;
    }

    private Object readCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double number = cell.getNumericCellValue();
                if (number == Math.floor(number)) {
                    yield String.valueOf((long) number);
                }
                yield String.valueOf(number);
            }
            case BOOLEAN -> cell.getBooleanCellValue();
            case FORMULA -> cell.getCellFormula();
            case BLANK -> "";
            default -> "";
        };
    }

    private boolean isEmptyRow(Row row) {
        for (Cell cell : row) {
            Object value = readCellValue(cell);

            if (value != null && !value.toString().isBlank()) {
                return false;
            }
        }

        return true;
    }

    private String normalizeHeader(String value) {
        return value.trim()
                .toUpperCase()
                .replace(" ", "_")
                .replace("-", "_");
    }

    private String valueAsString(Object value) {
        return value == null ? null : value.toString().trim();
    }

    private record ParsedTable(List<String> headers, List<Map<String, Object>> rows) {
    }
}
