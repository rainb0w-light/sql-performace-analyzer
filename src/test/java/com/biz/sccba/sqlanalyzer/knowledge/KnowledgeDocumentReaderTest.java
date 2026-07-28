package com.biz.sccba.sqlanalyzer.knowledge;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeDocumentReaderTest {

    private final KnowledgeDocumentReader reader =
            new KnowledgeDocumentReader(1024 * 1024, 100, 5_000, 4 * 1024 * 1024);

    @Test
    void readsMarkdownAndTextWithStableMinimumLocator() {
        var markdown = reader.read("guide.md", "text/markdown", "# Loan\n\nOVERDUE means late.".getBytes());
        var text = reader.read("guide.txt", "text/plain", "ACTIVE\nCLOSED".getBytes());

        assertTrue(markdown.chunks().getFirst().text().contains("OVERDUE"));
        assertEquals("chunk:0", markdown.chunks().getFirst().locator());
        assertTrue(text.chunks().getFirst().text().contains("ACTIVE"));
        assertEquals("chunk:0", text.chunks().getFirst().locator());
    }

    @Test
    void readsPdfWithoutInventingPageLocator() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText("Published loan policy");
                content.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            pdf = out.toByteArray();
        }

        var result = reader.read("policy.pdf", "application/pdf", pdf);
        assertTrue(result.chunks().getFirst().text().contains("loan policy"));
        assertEquals("chunk:0", result.chunks().getFirst().locator());
    }

    @Test
    void readsXlsxAsUnstructuredDocument() throws Exception {
        byte[] xlsx;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("policy");
            sheet.createRow(0).createCell(0).setCellValue("loan status ACTIVE");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            xlsx = out.toByteArray();
        }

        var result = reader.read(
                "policy.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsx);
        assertTrue(result.chunks().stream().anyMatch(chunk -> chunk.text().contains("loan status ACTIVE")));
        assertEquals("chunk:0", result.chunks().getFirst().locator());
    }

    @Test
    void rejectsMismatchedContentAndConfiguredLimits() {
        assertThrows(IllegalArgumentException.class,
                () -> reader.read("fake.pdf", "application/pdf", "not a pdf".getBytes()));
        assertThrows(IllegalArgumentException.class,
                () -> reader.read("script.exe", "application/octet-stream", new byte[]{1}));
        assertThrows(IllegalArgumentException.class,
                () -> new KnowledgeDocumentReader(3, 100, 5_000, 100)
                        .read("a.txt", "text/plain", "four".getBytes()));
    }
}
