package com.knowledge.worker.parser.impl.parsers;
import com.knowledge.common.domain.input.FileReference;
import com.knowledge.common.domain.parse.ParseElement;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.enums.parse.ElementType;
import com.knowledge.worker.parser.ParseContext;
import com.knowledge.worker.parser.ParseProperties;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PDFBox 原生解析器单测：段落聚合、页级指标、HEADER 识别、表格列对齐启发式。
 *
 * @author cxxl
 */
class PdfBoxDocumentParserTest {

    private final PdfBoxDocumentParser parser = new PdfBoxDocumentParser();

    private ParseContext context(byte[] data) {
        ParseContext context = new ParseContext();
        FileReference fileRef = new FileReference();
        fileRef.setFileId("F-1");
        fileRef.setFileName("t.pdf");
        fileRef.setMimeType("application/pdf");
        context.setFileRef(fileRef);
        context.setInputStream(new ByteArrayInputStream(data));
        context.setProperties(new ParseProperties());
        return context;
    }

    private byte[] buildPdf() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            for (int page = 0; page < 2; page++) {
                PDPage pdfPage = new PDPage(new PDRectangle(595, 842));
                doc.addPage(pdfPage);
                try (PDPageContentStream cs = new PDPageContentStream(doc, pdfPage)) {
                    // 页眉：两页同位置同文本（顶部）
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                    cs.newLineAtOffset(250, 800);
                    cs.showText("XX Project Header");
                    cs.endText();
                    // 正文两行
                    cs.beginText();
                    cs.newLineAtOffset(72, 700);
                    cs.showText("Chapter one body line " + page);
                    cs.endText();
                    cs.beginText();
                    cs.newLineAtOffset(72, 680);
                    cs.showText("Chapter one body continues " + page);
                    cs.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void pdfShouldParseParagraphsAndHeader() throws Exception {
        ParseContext context = context(buildPdf());
        ParseSource source = parser.parse(context);

        assertEquals(2, source.getUnitCount());
        assertEquals(2, context.getFileRef().getPageCount());
        assertEquals(2, source.getPageMetrics().size());
        assertTrue(source.getPageMetrics().getFirst().getCharCount() > 0);
        assertTrue(source.getPageMetrics().getFirst().getGarbledRatio() < 0.2);

        // HEADER：两页同位置重复文本
        List<ParseElement> headers = source.getElements().stream()
                .filter(e -> ElementType.HEADER.name().equals(e.getType()))
                .toList();
        assertEquals(1, headers.size());
        assertEquals("XXProjectHeader", headers.getFirst().getText());

        // 段落：正文（页眉除外）
        List<ParseElement> paragraphs = source.getElements().stream()
                .filter(e -> ElementType.PARAGRAPH.name().equals(e.getType()))
                .toList();
        assertTrue(paragraphs.size() >= 2);
        assertTrue(paragraphs.stream().anyMatch(p -> p.getText().contains("Chapteronebody")));
        // 溯源与坐标
        assertNotNull(paragraphs.getFirst().getProvenance());
        assertTrue(paragraphs.getFirst().getProvenance().getPath().startsWith("pdf#page"));
        assertNotNull(paragraphs.getFirst().getBbox());
    }

    @Test
    void scannedPdfShouldEmitZeroCharMetrics() throws Exception {
        // 空白页（无文本层）→ 页级字符数为 0（扫描判定交给 SignalDetector）
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(new PDRectangle(595, 842)));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            ParseSource source = parser.parse(context(out.toByteArray()));
            assertEquals(1, source.getUnitCount());
            assertEquals(0, source.getPageMetrics().getFirst().getCharCount());
        }
    }

    @Test
    void alignedColumnsShouldFormTable() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage pdfPage = new PDPage(new PDRectangle(595, 842));
            doc.addPage(pdfPage);
            try (PDPageContentStream cs = new PDPageContentStream(doc, pdfPage)) {
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                // 三行、三列，列距 > 20pt（宽列距信号）
                String[][] rows = {{"Score", "Standard", "Points"}, {"A1", "Bidding price", "30"}, {"A2", "Tech plan", "40"}};
                float y = 700;
                for (String[] row : rows) {
                    cs.beginText();
                    cs.newLineAtOffset(60, y);
                    cs.showText(row[0]);
                    cs.newLineAtOffset(130, 0);
                    cs.showText(row[1]);
                    cs.newLineAtOffset(150, 0);
                    cs.showText(row[2]);
                    cs.endText();
                    y -= 24;
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            ParseSource source = parser.parse(context(out.toByteArray()));

            List<ParseElement> tables = source.getElements().stream()
                    .filter(e -> ElementType.TABLE.name().equals(e.getType()))
                    .toList();
            assertEquals(1, tables.size(), () -> "elements=" + source.getElements());
            assertEquals(3, tables.getFirst().getRows());
            assertEquals(3, tables.getFirst().getCols());
            assertEquals("Score", tables.getFirst().getCells().getFirst().getText());
            assertTrue(tables.getFirst().getCells().getFirst().getIsHeader());
        }
    }

    @Test
    void repeatedFooterAndPageNumberShouldEmitFooterElements() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            for (int page = 0; page < 2; page++) {
                PDPage pdfPage = new PDPage(new PDRectangle(595, 842));
                doc.addPage(pdfPage);
                try (PDPageContentStream cs = new PDPageContentStream(doc, pdfPage)) {
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                    // 正文
                    cs.beginText();
                    cs.newLineAtOffset(72, 400);
                    cs.showText("Body text of page " + page);
                    cs.endText();
                    // 页脚：两页同位置同文本（页底）
                    cs.beginText();
                    cs.newLineAtOffset(250, 30);
                    cs.showText("XXProjectFooter");
                    cs.endText();
                    // 页码：每页不同（页码模式；与页脚文本不同行，避免行聚合合并）
                    cs.beginText();
                    cs.newLineAtOffset(500, 18);
                    cs.showText(String.valueOf(page + 1));
                    cs.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            ParseSource source = parser.parse(context(out.toByteArray()));

            List<ParseElement> footers = source.getElements().stream()
                    .filter(e -> ElementType.FOOTER.name().equals(e.getType()))
                    .toList();
            assertEquals(2, footers.size(), () -> "footers=" + footers + " all=" + source.getElements());
            assertTrue(footers.stream().anyMatch(f -> "XXProjectFooter".equals(f.getText())));
            assertTrue(footers.stream().anyMatch(f -> "1".equals(f.getText())), () -> "footers=" + footers);
            // 页脚不进正文流
            List<ParseElement> paragraphs = source.getElements().stream()
                    .filter(e -> ElementType.PARAGRAPH.name().equals(e.getType()))
                    .toList();
            assertTrue(paragraphs.stream().noneMatch(p -> p.getText().contains("XXProjectFooter")));
        }
    }

    @Test
    void footerWithTrailingPageNumberShouldStripSuffixAndDetect() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            for (int page = 0; page < 2; page++) {
                PDPage pdfPage = new PDPage(new PDRectangle(595, 842));
                doc.addPage(pdfPage);
                try (PDPageContentStream cs = new PDPageContentStream(doc, pdfPage)) {
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                    // 正文
                    cs.beginText();
                    cs.newLineAtOffset(72, 400);
                    cs.showText("Body text of page " + page);
                    cs.endText();
                    // 页脚与页码同行：行聚合后为 XXProjectFooter1 / XXProjectFooter2
                    cs.beginText();
                    cs.newLineAtOffset(250, 30);
                    cs.showText("XXProjectFooter");
                    cs.newLineAtOffset(150, 0);
                    cs.showText(String.valueOf(page + 1));
                    cs.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            ParseSource source = parser.parse(context(out.toByteArray()));

            List<ParseElement> footers = source.getElements().stream()
                    .filter(e -> ElementType.FOOTER.name().equals(e.getType()))
                    .toList();
            assertEquals(1, footers.size(), () -> "footers=" + footers);
            assertEquals("XXProjectFooter", footers.getFirst().getText());
            // 页脚不进正文流
            List<ParseElement> paragraphs = source.getElements().stream()
                    .filter(e -> ElementType.PARAGRAPH.name().equals(e.getType()))
                    .toList();
            assertTrue(paragraphs.stream().noneMatch(p -> p.getText().contains("XXProjectFooter")));
        }
    }

    @Test
    void tocLineShouldMarkTocCandidate() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage pdfPage = new PDPage(new PDRectangle(595, 842));
            doc.addPage(pdfPage);
            try (PDPageContentStream cs = new PDPageContentStream(doc, pdfPage)) {
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                // 目录行：标题 + 点线引导符 + 行尾页码
                cs.beginText();
                cs.newLineAtOffset(72, 700);
                cs.showText("Chapter One .... 1");
                cs.endText();
                // 正文行：行尾无页码
                cs.beginText();
                cs.newLineAtOffset(72, 680);
                cs.showText("This is body paragraph text");
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            ParseSource source = parser.parse(context(out.toByteArray()));

            assertTrue(source.getElements().stream()
                    .filter(e -> ElementType.PARAGRAPH.name().equals(e.getType()))
                    .anyMatch(e -> Boolean.TRUE.equals(e.getTocCandidate())
                            && e.getText().contains("ChapterOne....1")));
        }
    }
}
