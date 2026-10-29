package com.calipso.campaign;

import java.util.List;
import java.util.Map;

public record FileAnalysisResponse(
        String fileName,
        List<String> headers,
        String detectedPhoneColumn,
        String detectedEmailColumn,
        String detectedNameColumn,
        int totalRows,
        List<Map<String, Object>> sampleRows,
        List<Map<String, Object>> allRows,
        Map<String, String> headerLabels,
        List<String> sheetNames,
        int sheetIndex,
        int headerRow
) {
}
