package com.calipso.excel;


import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.calipso.campaign.Campaign;
import com.calipso.campaign.CampaignRepository;
import com.calipso.campaign.CampaignStatus;
import com.calipso.excelvariable.ExcelVariable;
import com.calipso.excelvariable.ExcelVariableRepository;
import com.calipso.recipient.CampaignRecipient;
import com.calipso.recipient.CampaignRecipientRepository;
import com.calipso.recipient.RecipientStatus;
import com.calipso.sms.SmsMessageService;

import java.io.InputStream;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ExcelImportService {

    private final CampaignRepository campaignRepository;
    private final ExcelVariableRepository variableRepository;
    private final CampaignRecipientRepository recipientRepository;
    private final SmsMessageService smsMessageService;

    public Campaign importExcel(Long campaignId, MultipartFile file) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("Campagne introuvable"));

        List<ExcelVariable> variables = variableRepository.findByProfileIdAndActiveTrue(
                campaign.getProfile().getId()
        );

        if (variables.isEmpty()) {
            throw new RuntimeException("Aucune variable configurée pour ce profil");
        }

        try (InputStream inputStream = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            Sheet sheet = workbook.getSheetAt(0);

            Row headerRow = sheet.getRow(0);

            if (headerRow == null) {
                throw new RuntimeException("Le fichier Excel ne contient pas d'entêtes");
            }

            List<String> headers = readHeaders(headerRow);

            validateRequiredHeaders(headers, variables);

            int total = 0;
            int valid = 0;
            int invalid = 0;
            int totalSegments = 0;

            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);

                if (row == null || isEmptyRow(row)) {
                    continue;
                }

                total++;

                Map<String, Object> rawData = readRowData(row, headers);

                String phoneCode = campaign.getPhoneVariable().getCode();
                String phoneNumber = valueAsString(rawData.get(phoneCode));

                CampaignRecipient recipient = new CampaignRecipient();
                recipient.setCampaign(campaign);
                recipient.setRawData(rawData);
                recipient.setPhoneNumber(normalizePhone(phoneNumber));

                if (phoneNumber == null || phoneNumber.isBlank()) {
                    recipient.setStatus(RecipientStatus.INVALID);
                    recipient.setErrorMessage("Numéro de téléphone vide");
                    invalid++;
                } else {
                    String message = smsMessageService.generateMessage(
                            campaign.getTemplate().getContent(),
                            rawData
                    );

                    int segments = smsMessageService.calculateSmsSegments(message);

                    recipient.setGeneratedMessage(message);
                    recipient.setSegmentCount(segments);
                    recipient.setStatus(RecipientStatus.VALID);

                    valid++;
                    totalSegments += segments;
                }

                recipientRepository.save(recipient);
            }

            campaign.setTotalRecipients(total);
            campaign.setTotalValid(valid);
            campaign.setTotalInvalid(invalid);
            campaign.setTotalSegments(totalSegments);
            campaign.setStatus(CampaignStatus.IMPORTED);

            return campaignRepository.save(campaign);

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors de l'import Excel : " + e.getMessage(), e);
        }
    }

    private List<String> readHeaders(Row headerRow) {
        List<String> headers = new ArrayList<>();

        for (Cell cell : headerRow) {
            String value = cell.getStringCellValue();

            if (value != null && !value.isBlank()) {
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

    private String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }

        String cleaned = phone.replace(" ", "")
                .replace("-", "")
                .replace(".", "")
                .trim();

        if (cleaned.startsWith("+")) {
            return cleaned;
        }

        if (cleaned.startsWith("225")) {
            return "+" + cleaned;
        }

        if (cleaned.startsWith("01") || cleaned.startsWith("05") || cleaned.startsWith("07")) {
            return "+225" + cleaned;
        }

        return cleaned;
    }

    private String valueAsString(Object value) {
        return value == null ? null : value.toString();
    }
}
