package com.calipso.excel;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class ExcelTableReader {
    public record Table(List<String> headers, Map<String, String> headerLabels,
                        List<Map<String, Object>> rows, List<String> sheetNames) {}

    public Table read(MultipartFile file, int sheetIndex, int headerRow) {
        if (file.isEmpty() || headerRow < 1 || sheetIndex < 0) {
            throw new IllegalArgumentException("Fichier vide ou position des en-têtes invalide.");
        }
        String name = Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".csv")) return readCsv(file, sheetIndex, headerRow);
            if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
                throw new IllegalArgumentException("Formats acceptés : .xlsx, .xls et .csv.");
            }
            try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
                if (sheetIndex >= workbook.getNumberOfSheets()) throw new IllegalArgumentException("Feuille introuvable.");
                List<String> names = new ArrayList<>();
                for (int i = 0; i < workbook.getNumberOfSheets(); i++) names.add(workbook.getSheetName(i));
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                Row row = sheet.getRow(headerRow - 1);
                if (row == null) throw new IllegalArgumentException("La ligne choisie ne contient aucun en-tête.");
                DataFormatter formatter = new DataFormatter(Locale.ROOT);
                FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
                List<String> labels = new ArrayList<>();
                for (int i = 0; i < row.getLastCellNum(); i++) labels.add(formatter.formatCellValue(row.getCell(i), evaluator).trim());
                Map<String, String> headers = headers(labels);
                List<Map<String, Object>> rows = new ArrayList<>();
                for (int r = headerRow; r <= sheet.getLastRowNum(); r++) {
                    Row data = sheet.getRow(r);
                    if (data == null) continue;
                    Map<String, Object> values = new LinkedHashMap<>();
                    int c = 0;
                    for (String key : headers.keySet()) {
                        Cell cell = data.getCell(c++);
                        String value = cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)
                                ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                                : formatter.formatCellValue(cell, evaluator).trim();
                        values.put(key, value);
                    }
                    if (values.values().stream().anyMatch(v -> !v.toString().isBlank())) rows.add(values);
                }
                return new Table(new ArrayList<>(headers.keySet()), headers, rows, names);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Impossible de lire le fichier : " + e.getMessage(), e);
        }
    }

    private Map<String, String> headers(List<String> labels) {
        while (!labels.isEmpty() && labels.getLast().isBlank()) labels.removeLast();
        if (labels.isEmpty()) throw new IllegalArgumentException("La ligne choisie ne contient aucun en-tête.");
        Map<String, String> result = new LinkedHashMap<>();
        for (String label : labels) {
            String code = HeaderNames.normalize(label);
            if (code.isEmpty()) throw new IllegalArgumentException("Un en-tête est vide entre deux colonnes. Nommez chaque colonne.");
            if (result.putIfAbsent(code, label) != null) throw new IllegalArgumentException("En-tête en double : " + label);
        }
        return result;
    }

    private Table readCsv(MultipartFile file, int sheetIndex, int headerRow) throws IOException {
        if (sheetIndex != 0) throw new IllegalArgumentException("Un CSV contient une seule feuille.");
        String content = new String(file.getBytes(), StandardCharsets.UTF_8).replaceFirst("^\\uFEFF", "");
        // Ignore separators inside quoted cells when detecting the delimiter.
        char delimiter = detectDelimiter(content, headerRow);
        List<List<String>> records = csvRecords(content, delimiter);
        if (headerRow > records.size()) throw new IllegalArgumentException("Ligne d'en-têtes introuvable.");
        Map<String, String> headers = headers(new ArrayList<>(records.get(headerRow - 1)));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int r = headerRow; r < records.size(); r++) {
            List<String> record = records.get(r);
            Map<String, Object> values = new LinkedHashMap<>();
            int c = 0;
            for (String key : headers.keySet()) values.put(key, c < record.size() ? record.get(c++).trim() : "");
            if (values.values().stream().anyMatch(v -> !v.toString().isBlank())) rows.add(values);
        }
        return new Table(new ArrayList<>(headers.keySet()), headers, rows, List.of("CSV"));
    }

    private char detectDelimiter(String content, int headerRow) {
        List<List<String>> semicolon = csvRecords(content, ';');
        List<List<String>> comma = csvRecords(content, ',');
        int index = Math.min(headerRow - 1, Math.max(0, semicolon.size() - 1));
        return !semicolon.isEmpty() && semicolon.get(index).size() > comma.get(index).size() ? ';' : ',';
    }

    private List<List<String>> csvRecords(String content, char delimiter) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < content.length() && content.charAt(i + 1) == '"') { cell.append('"'); i++; }
                else quoted = !quoted;
            } else if (!quoted && ch == delimiter) { record.add(cell.toString()); cell.setLength(0); }
            else if (!quoted && (ch == '\n' || ch == '\r')) {
                if (ch == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') i++;
                record.add(cell.toString()); records.add(record); record = new ArrayList<>(); cell.setLength(0);
            } else cell.append(ch);
        }
        if (quoted) throw new IllegalArgumentException("Guillemets non fermés dans le fichier CSV.");
        if (!record.isEmpty() || !cell.isEmpty()) { record.add(cell.toString()); records.add(record); }
        return records;
    }
}
