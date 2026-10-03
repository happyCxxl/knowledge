package com.knowledge.worker.parser.impl.parsers;
import com.knowledge.common.domain.input.FileReference;
import com.knowledge.common.domain.parse.ParseElement;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.enums.parse.ElementType;
import com.knowledge.worker.parser.ParseContext;
import com.knowledge.worker.parser.ParseProperties;
import com.knowledge.worker.parser.impl.parsers.pdf.CharInfo;
import com.knowledge.worker.parser.impl.parsers.pdf.HeaderFooterDetector;
import com.knowledge.worker.parser.impl.parsers.pdf.PageLine;
import com.knowledge.worker.parser.impl.parsers.pdf.RuleLines;
import com.knowledge.worker.parser.impl.parsers.pdf.TableCandidateDetector;
import com.knowledge.worker.parser.impl.parsers.pdf.Token;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        assertEquals("XX Project Header", headers.getFirst().getText());

        // 段落：正文（页眉除外）
        List<ParseElement> paragraphs = source.getElements().stream()
                .filter(e -> ElementType.PARAGRAPH.name().equals(e.getType()))
                .toList();
        assertTrue(paragraphs.size() >= 2);
        assertTrue(paragraphs.stream().anyMatch(p -> p.getText().contains("Chapter one body")));
        // 行间拼接：同一段落内的相邻行之间补空格
        assertTrue(paragraphs.stream()
                .anyMatch(p -> p.getText().contains("line 0 Chapter one body continues")));
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
            // 格内多词完整保留（词边界补空格）
            assertEquals("Bidding price", tables.getFirst().getCells().get(4).getText());
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
                            && e.getText().contains("Chapter One .... 1")));
        }
    }

    @Test
    void englishWordBoundariesShouldKeepSpaces() {
        List<CharInfo> chars = new ArrayList<>();
        chars.addAll(word("Chapter", 72));
        chars.addAll(word("one", 110));
        chars.addAll(word("body", 130));

        assertEquals("Chapter one body", PdfBoxDocumentParser.lineText(chars));
    }

    @Test
    void cjkWordBoundariesShouldNotInsertSpaces() {
        List<CharInfo> chars = new ArrayList<>();
        // 每个汉字各成一次回调（PDFBox 对 CJK 常逐字回调）
        chars.add(ch('知', 72, true, true));
        chars.add(ch('识', 82, true, true));
        chars.add(ch('库', 92, true, true));

        assertEquals("知识库", PdfBoxDocumentParser.lineText(chars));
    }

    @Test
    void mixedScriptBoundaryShouldKeepSpace() {
        List<CharInfo> chars = new ArrayList<>();
        chars.add(ch('知', 72, true, true));
        chars.add(ch('识', 82, true, true));
        chars.add(ch('库', 92, true, true));
        chars.addAll(word("knowledge", 110));

        assertEquals("知识库 knowledge", PdfBoxDocumentParser.lineText(chars));
    }

    @Test
    void lineSeparatorShouldFollowCjkRule() {
        assertTrue(PdfBoxDocumentParser.lineSeparator(line("知识库"), line("下一句")).isEmpty());
        assertEquals(" ", PdfBoxDocumentParser.lineSeparator(line("Chapter one"), line("body")));
    }

    @Test
    void cjkCodePointShouldCoverHanAndFullwidthPunctuation() {
        assertTrue(PdfBoxDocumentParser.isCjk('中'));
        assertTrue(PdfBoxDocumentParser.isCjk('。'));
        assertTrue(PdfBoxDocumentParser.isCjk('，'));
        assertTrue(PdfBoxDocumentParser.isCjk('ア'));
        assertFalse(PdfBoxDocumentParser.isCjk('A'));
        assertFalse(PdfBoxDocumentParser.isCjk(','));
    }

    @Test
    void headerFooterKeyShouldNormalizeWhitespace() {
        assertEquals("XX Project Header", HeaderFooterDetector.normalizeKey("  XX   Project \t Header "));
        assertEquals("知识库", HeaderFooterDetector.normalizeKey("\u200B知识库"));
        assertEquals("", HeaderFooterDetector.normalizeKey("   "));
    }

    @Test
    void borderedTableShouldUseRuleGridWithSpanAndSparseRow() throws Exception {
        ParseSource source = parser.parse(context(buildRuledTable()));

        List<ParseElement> tables = source.getElements().stream()
                .filter(e -> ElementType.TABLE.name().equals(e.getType()))
                .toList();
        assertEquals(1, tables.size(), () -> "elements=" + source.getElements());
        ParseElement table = tables.getFirst();
        // 三行三列；首行横向合并成一格，第二行只填一格也留在同一张表内
        assertEquals(3, table.getRows());
        assertEquals(3, table.getCols());
        assertEquals(0, table.getHeaderRow());
        assertEquals(7, table.getCells().size());

        ParseElement merged = table.getCells().stream()
                .filter(c -> c.getRow() == 0 && c.getCol() == 0).findFirst().orElse(null);
        assertNotNull(merged);
        assertEquals(3, merged.getColSpan());
        assertEquals("Score sheet", merged.getText());
        assertTrue(merged.getIsHeader());

        assertEquals("A1", cellText(table, 1, 0));
        assertEquals("Bidding price", cellText(table, 1, 1));
        assertEquals("30", cellText(table, 2, 2));
    }

    @Test
    void ruledEmptyGridShouldNotProduceTable() throws Exception {
        ParseSource source = parser.parse(context(buildRuledTable(false)));

        assertTrue(source.getElements().stream()
                .noneMatch(e -> ElementType.TABLE.name().equals(e.getType())));
    }

    @Test
    void sparseRuledGridShouldBeRejectedByFilledGate() throws Exception {
        ParseSource source = parser.parse(context(buildSparseRuledGrid()));

        assertTrue(source.getElements().stream()
                .noneMatch(e -> ElementType.TABLE.name().equals(e.getType())));
        assertTrue(source.getFacts().stream()
                .anyMatch(f -> f.getEvidence() != null && f.getEvidence().contains("空白率")));
    }

    @Test
    void numericFirstRowShouldHaveNoHeader() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage pdfPage = new PDPage(new PDRectangle(595, 842));
            doc.addPage(pdfPage);
            try (PDPageContentStream cs = new PDPageContentStream(doc, pdfPage)) {
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                String[][] rows = {{"2024", "Q1", "100"}, {"2025", "Q2", "200"}, {"2026", "Q3", "300"}};
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
            assertEquals(null, tables.getFirst().getHeaderRow());
            assertTrue(tables.getFirst().getCells().stream()
                    .noneMatch(c -> Boolean.TRUE.equals(c.getIsHeader())));
        }
    }

    @Test
    void supportedColumnsShouldDropUnsupportedColumns() {
        List<List<Token>> matrix = List.of(
                List.of(new Token("A", 10, 20), new Token("B", 100, 110)),
                List.of(new Token("C", 10, 20), new Token("D", 100, 110)),
                List.of(new Token("E", 10, 20), new Token("X", 200, 210)));

        assertEquals(List.of(10.0, 100.0), TableCandidateDetector.supportedColumns(matrix, 2));
    }

    @Test
    void detectHeaderRowShouldFollowNumericRule() {
        assertEquals(0, PdfBoxDocumentParser.detectHeaderRow(
                List.of(List.of("项目", "分值"), List.of("A1", "30")), List.of(10.0, 10.0), 0.3));
        assertNull(PdfBoxDocumentParser.detectHeaderRow(
                List.of(List.of("2024", "100"), List.of("2025", "200")), List.of(10.0, 10.0), 0.3));
        assertEquals(0, PdfBoxDocumentParser.detectHeaderRow(
                List.of(List.of("项目", "分值"), List.of("A1", "B1")), List.of(14.0, 10.0), 0.3));
    }

    @Test
    void mergedCellsShouldMergeAcrossMissingDividers() {
        RuleLines.Grid grid = new RuleLines.Grid(
                List.of(0.0, 10.0, 20.0), List.of(0.0, 10.0, 20.0),
                List.of(List.of(true), List.of(false), List.of(false)),
                List.of(List.of(false, false), List.of(false, false)));

        List<PdfBoxDocumentParser.CellRect> rects = PdfBoxDocumentParser.mergedCells(grid);

        assertEquals(3, rects.size());
        assertEquals(0, rects.getFirst().row());
        assertEquals(2, rects.getFirst().colSpan());
        assertEquals(1, rects.getFirst().rowSpan());
    }

    private String cellText(ParseElement table, int row, int col) {
        return table.getCells().stream()
                .filter(c -> c.getRow() == row && c.getCol() == col)
                .map(ParseElement::getText).findFirst().orElse(null);
    }

    /** 造一张带框线的表：三行三列，首行横向合并、第二行只填一格 */
    private byte[] buildRuledTable() throws Exception {
        return buildRuledTable(true);
    }

    /** 造一张带框线的表；withText 为 false 时只画线不写文字（空白率门禁用例） */
    private byte[] buildRuledTable(boolean withText) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(595, 842));
            doc.addPage(page);
            float left = 72;
            float right = 372;
            float top = 842 - 200;
            float middle = 842 - 230;
            float lower = 842 - 260;
            float bottom = 842 - 290;
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setLineWidth(0.8f);
                drawLine(cs, left, top, right, top);
                drawLine(cs, left, middle, right, middle);
                drawLine(cs, left, lower, right, lower);
                drawLine(cs, left, bottom, right, bottom);
                drawLine(cs, left, bottom, left, top);
                drawLine(cs, right, bottom, right, top);
                drawLine(cs, 172, bottom, 172, middle);
                drawLine(cs, 272, bottom, 272, middle);
                if (withText) {
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                    cs.beginText();
                    cs.newLineAtOffset(left + 10, middle + 8);
                    cs.showText("Score sheet");
                    cs.endText();
                    cs.beginText();
                    cs.newLineAtOffset(left + 10, lower + 8);
                    cs.showText("A1");
                    cs.endText();
                    cs.beginText();
                    cs.newLineAtOffset(182, lower + 8);
                    cs.showText("Bidding price");
                    cs.endText();
                    cs.beginText();
                    cs.newLineAtOffset(282, bottom + 8);
                    cs.showText("30");
                    cs.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** 造一张 2 行 6 列的线框网格，只填一格（填充率低于假表门限） */
    private byte[] buildSparseRuledGrid() throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(595, 842));
            doc.addPage(page);
            float left = 72;
            float right = 372;
            float top = 842 - 200;
            float middle = 842 - 245;
            float bottom = 842 - 290;
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setLineWidth(0.8f);
                drawLine(cs, left, top, right, top);
                drawLine(cs, left, middle, right, middle);
                drawLine(cs, left, bottom, right, bottom);
                for (int i = 0; i <= 6; i++) {
                    float x = left + i * 50;
                    drawLine(cs, x, bottom, x, top);
                }
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                cs.beginText();
                cs.newLineAtOffset(left + 10, top - 8);
                cs.showText("X");
                cs.endText();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private void drawLine(PDPageContentStream cs, float x1, float y1, float x2, float y2) throws Exception {
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);
        cs.stroke();
    }

    /** 造一个词的字符序列：词内 x 连续，首末码点带词边界标记 */
    private List<CharInfo> word(String text, double x) {
        List<CharInfo> chars = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            chars.add(ch(text.charAt(i), x + i * 5, i == 0, i == text.length() - 1));
        }
        return chars;
    }

    /** 造一个字符事实（固定行高与字号） */
    private CharInfo ch(int codePoint, double x, boolean wordStart, boolean wordEnd) {
        return new CharInfo(x, 700, 5, 10, 10, "Helvetica", false,
                codePoint, wordStart, wordEnd);
    }

    /** 造一个行事实（只用到文本） */
    private PageLine line(String text) {
        return new PageLine(1, 72, 700, 100, 10, text, 10, "Helvetica", false,
                List.of(), List.of(), false);
    }
}
