package com.calipso.excel;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class ExcelTableReaderTest {
    private final ExcelTableReader reader = new ExcelTableReader();

    @Test
    void readsSelectedSheetAndHeaderLineAndKeepsPhoneFormatting() throws Exception {
        try (var book = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            book.createSheet("Instructions").createRow(0).createCell(0).setCellValue("Instructions");
            var sheet = book.createSheet("Clients");
            sheet.createRow(0).createCell(0).setCellValue("Liste des clients");
            var header = sheet.createRow(2);
            header.createCell(0).setCellValue("Téléphone client"); header.createCell(1).setCellValue("Prénom");
            var row = sheet.createRow(3); row.createCell(0).setCellValue(701234567);
            var style = book.createCellStyle(); style.setDataFormat(book.createDataFormat().getFormat("0000000000"));
            row.getCell(0).setCellStyle(style); row.createCell(1).setCellValue("Awa");
            book.write(bytes);
            var result = reader.read(new MockMultipartFile("file", "clients.xlsx", "", bytes.toByteArray()), 1, 3);
            assertEquals(java.util.List.of("Instructions", "Clients"), result.sheetNames());
            assertEquals("Téléphone client", result.headerLabels().get("TELEPHONE_CLIENT"));
            assertEquals("0701234567", result.rows().getFirst().get("TELEPHONE_CLIENT"));
            assertEquals("Awa", result.rows().getFirst().get("PRENOM"));
        }
    }

    @Test
    void acceptsHeaderOnlyFilesForCreatingModels() {
        var result = reader.read(csv("Téléphone;Prénom\n"), 0, 1);
        assertEquals(2, result.headers().size()); assertTrue(result.rows().isEmpty());
    }

    @Test
    void handlesQuotedSeparatorsEscapedQuotesAndMultilineCells() {
        var result = reader.read(csv("Titre\nTéléphone;Nom;Note\n0701234567;\"Kouassi; Awa\";\"Bonjour \"\"Awa\"\"\nSuite\"\n"), 0, 2);
        assertEquals("Kouassi; Awa", result.rows().getFirst().get("NOM"));
        assertEquals("Bonjour \"Awa\"\nSuite", result.rows().getFirst().get("NOTE"));
    }

    @Test
    void rejectsDuplicateNormalizedHeaders() {
        assertThrows(IllegalArgumentException.class, () -> reader.read(csv("Prénom;Prenom\nAwa;Marc"), 0, 1));
    }

    @Test
    void rejectsHeaderGapsInsteadOfShiftingData() {
        assertThrows(IllegalArgumentException.class, () -> reader.read(csv("Nom;;Téléphone\nAwa;;0701234567"), 0, 1));
    }

    @Test
    void rejectsMissingHeaderRowAndInvalidCsvQuotes() {
        assertThrows(IllegalArgumentException.class, () -> reader.read(csv("Nom"), 0, 2));
        assertThrows(IllegalArgumentException.class, () -> reader.read(csv("Nom\n\"Awa"), 0, 1));
    }

    private MockMultipartFile csv(String text) {
        return new MockMultipartFile("file", "clients.csv", "text/csv", text.getBytes(StandardCharsets.UTF_8));
    }
}
