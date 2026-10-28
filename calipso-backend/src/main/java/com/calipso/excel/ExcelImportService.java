package com.calipso.excel;

import com.calipso.auth.CompanyAccess;

import com.calipso.campaign.Campaign;
import com.calipso.campaign.CampaignChannel;
import com.calipso.campaign.CampaignRepository;
import com.calipso.campaign.CampaignStatus;
import com.calipso.campaign.CampaignHeaderService;
import com.calipso.campaign.FileAnalysisResponse;
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

import java.io.ByteArrayOutputStream;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ExcelImportService {

    private final CompanyAccess access;

    private final ExcelTableReader tableReader;
    private final CampaignHeaderService campaignHeaders;
    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository recipientRepository;
    private final SmsMessageService smsMessageService;
    private final MultichannelGatewayService gatewayService;

    public FileAnalysisResponse analyzeFile(MultipartFile file) {
        return analyzeFile(file, 0, 1);
    }

    public FileAnalysisResponse analyzeFile(MultipartFile file, int sheetIndex, int headerRow) {
        ExcelTableReader.Table table = tableReader.read(file, sheetIndex, headerRow);
        return new FileAnalysisResponse(file.getOriginalFilename(), table.headers(),
                detectPhoneColumn(table.headers(), table.rows()), detectEmailColumn(table.headers(), table.rows()),
                detectNameColumn(table.headers()), table.rows().size(), table.rows().stream().limit(10).toList(),
                table.rows(), table.headerLabels(), table.sheetNames(), sheetIndex, headerRow);
    }

    @Transactional
    public Campaign importExcel(Long campaignId, MultipartFile file) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("Campagne introuvable"));

        access.requireCampaign(campaign);
        if (campaign.getStatus() == CampaignStatus.COMPLETED || campaign.getStatus() == CampaignStatus.SENDING) {
            throw new IllegalArgumentException("Les destinataires d'une campagne envoyée ou en cours d'envoi ne peuvent plus être remplacés.");
        }

        Map<String, Object> configuration = campaign.getImportConfiguration();
        int sheetIndex = configuration != null && configuration.get("sheetIndex") instanceof Number value ? value.intValue() : 0;
        int headerRow = configuration != null && configuration.get("headerRow") instanceof Number value ? value.intValue() : 1;
        FileAnalysisResponse analysis = analyzeFile(file, sheetIndex, headerRow);
        if (analysis.totalRows() == 0) throw new IllegalArgumentException("Le fichier ne contient aucun destinataire.");

        if (campaign.getProfile() != null || campaign.getImportConfiguration() != null) {
            var prepared = campaignHeaders.prepareReimport(campaign, analysis);
            campaign.setImportConfiguration(prepared.snapshot());
            return importRowsIntoCampaign(campaign, prepared.rows(), prepared.recipientColumn());
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
        access.requireCampaign(campaign);
        if (campaign.getStatus() == CampaignStatus.COMPLETED || campaign.getStatus() == CampaignStatus.SENDING) {
            throw new IllegalArgumentException("Les destinataires d'une campagne envoyée ou en cours d'envoi ne peuvent plus être remplacés.");
        }
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

    private String valueAsString(Object value) {
        return value == null ? null : value.toString().trim();
    }

}
