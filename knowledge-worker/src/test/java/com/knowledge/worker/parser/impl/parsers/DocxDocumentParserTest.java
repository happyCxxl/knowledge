package com.knowledge.worker.parser.impl.parsers;
import com.knowledge.common.domain.input.FileReference;
import com.knowledge.common.domain.parse.ParseElement;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.domain.parse.signal.PageMetric;
import com.knowledge.common.enums.parse.ElementType;
import com.knowledge.worker.parser.ParseContext;
import com.knowledge.worker.parser.ParseProperties;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STMerge;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.List;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DOCX 原生解析器单测：段落/表格/合并、页眉页脚部件、目录行特征、supports 与能力标识。
 *
 * @author cxxl
 */
class DocxDocumentParserTest {

    private static final String MIME_DOCX =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final DocxDocumentParser parser = new DocxDocumentParser();

    private ParseContext context(byte[] data) {
        ParseContext context = new ParseContext();
        FileReference fileRef = new FileReference();
        fileRef.setFileId("F-1");
        fileRef.setFileName("t");
        fileRef.setMimeType(MIME_DOCX);
        context.setFileRef(fileRef);
        context.setInputStream(new ByteArrayInputStream(data));
        context.setProperties(new ParseProperties());
        return context;
    }

    private byte[] buildDocx() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph title = doc.createParagraph();
            title.setStyle("Heading1");
            XWPFRun titleRun = title.createRun();
            titleRun.setText("第一章 投标人须知");
            titleRun.setBold(true);
            titleRun.setFontSize(16.0);
            XWPFParagraph body = doc.createParagraph();
            body.createRun().setText("投标保证金为人民币叁佰万元整。");

            XWPFTable table = doc.createTable(2, 4);
            XWPFTableRow header = table.getRow(0);
            header.getCell(0).setText("评分项");
            header.getCell(1).setText("评分标准");
            // (0,1) 横向合并两列：真合并只对应一个 tc，占位 tc 不存在，其后 (0,3) 仍是真实单元格
            header.getCell(1).getCTTc().addNewTcPr().addNewGridSpan().setVal(BigInteger.valueOf(2));
            header.removeCell(2);
            header.getCell(2).setText("备注");
            table.getRow(1).getCell(0).setText("A1 报价");
            table.getRow(1).getCell(1).setText("低于基准价 1% 以内得 30 分");
            table.getRow(1).getCell(2).setText("30");
            table.getRow(1).getCell(3).setText("评分表");

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void docxShouldParseParagraphsTableAndMerges() throws Exception {
        ParseSource source = parser.parse(context(buildDocx()));

        assertEquals(1, source.getUnitCount());
        List<ParseElement> elements = source.getElements();
        ParseElement title = elements.stream()
                .filter(e -> "第一章 投标人须知".equals(e.getText()))
                .findFirst().orElse(null);
        assertNotNull(title);
        assertEquals(ElementType.PARAGRAPH.name(), title.getType());
        assertEquals(16.0, title.getFont().getSize());
        assertTrue(title.getFont().getBold());
        assertEquals("office#document.xml/paragraph[0]", title.getProvenance().getPath());

        ParseElement table = elements.stream()
                .filter(e -> ElementType.TABLE.name().equals(e.getType()))
                .findFirst().orElse(null);
        assertNotNull(table);
        assertEquals(2, table.getRows());
        assertEquals(4, table.getCols());
        assertEquals(0, table.getHeaderRow());
        // 7 个单元格：(0,0)(0,1 跨 2 列)(0,3)(1,0..3)；合并格之后的真实单元格不丢
        assertEquals(7, table.getCells().size());
        ParseElement merged = table.getCells().stream()
                .filter(c -> c.getRow() == 0 && c.getCol() == 1)
                .findFirst().orElse(null);
        assertNotNull(merged);
        assertEquals(2, merged.getColSpan());
        assertEquals("备注", cellText(table, 0, 3));
        assertEquals("30", cellText(table, 1, 2));
        assertEquals("评分表", cellText(table, 1, 3));
    }

    @Test
    void docxVerticalMergeShouldFillRowSpan() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFTable table = doc.createTable(3, 2);
            table.getRow(0).getCell(0).setText("评分项");
            table.getRow(0).getCell(1).setText("分值");
            table.getRow(1).getCell(0).setText("A1 报价");
            table.getRow(1).getCell(1).setText("30");
            // 第二行起纵向合并首列：行 1 restart、行 2 continue（continue 格不产出元素）
            tcPr(table.getRow(1).getCell(0)).addNewVMerge().setVal(STMerge.RESTART);
            table.getRow(2).getCell(1).setText("15");
            tcPr(table.getRow(2).getCell(0)).addNewVMerge().setVal(STMerge.CONTINUE);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);

            ParseSource source = parser.parse(context(out.toByteArray()));

            ParseElement tableElement = source.getElements().stream()
                    .filter(e -> ElementType.TABLE.name().equals(e.getType()))
                    .findFirst().orElse(null);
            assertNotNull(tableElement);
            assertEquals(3, tableElement.getRows());
            assertEquals(2, tableElement.getCols());
            ParseElement mergedCell = cell(tableElement, 1, 0);
            assertNotNull(mergedCell);
            assertEquals(2, mergedCell.getRowSpan());
            assertNull(cell(tableElement, 2, 0));
            assertEquals("15", cellText(tableElement, 2, 1));
        }
    }

    /** 取指定行列的单元格文本（不存在记 null） */
    private String cellText(ParseElement table, int row, int col) {
        ParseElement cell = cell(table, row, col);
        return cell == null ? null : cell.getText();    }

    /** 取指定行列的单元格元素（不存在记 null） */
    private ParseElement cell(ParseElement table, int row, int col) {
        return table.getCells().stream()
                .filter(c -> c.getRow() == row && c.getCol() == col)
                .findFirst().orElse(null);
    }

    /** 复用或新建单元格属性块 */
    private CTTcPr tcPr(XWPFTableCell cell) {
        return cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
    }

    @Test
    void docxHeaderFooterPartsShouldEmitElements() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph body = doc.createParagraph();
            body.createRun().setText("正文内容");
            XWPFHeader header = doc.createHeader(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
            header.createParagraph().createRun().setText("XX项目招标文件");
            XWPFFooter footer = doc.createFooter(org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
            footer.createParagraph().createRun().setText("第 1 页 共 2 页");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);

            ParseSource source = parser.parse(context(out.toByteArray()));

            ParseElement headerElement = source.getElements().stream()
                    .filter(e -> ElementType.HEADER.name().equals(e.getType()))
                    .findFirst().orElse(null);
            assertNotNull(headerElement);
            assertEquals("XX项目招标文件", headerElement.getText());
            assertTrue(headerElement.getProvenance().getPath().startsWith("docx#header"));

            ParseElement footerElement = source.getElements().stream()
                    .filter(e -> ElementType.FOOTER.name().equals(e.getType()))
                    .findFirst().orElse(null);
            assertNotNull(footerElement);
            assertTrue(footerElement.getText().contains("第 1 页"));
            assertTrue(footerElement.getProvenance().getPath().startsWith("docx#footer"));
        }
    }

    @Test
    void docxTocParagraphShouldMarkTocCandidate() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph toc = doc.createParagraph();
            toc.createRun().setText("第一章 总则 ...... 1");
            XWPFParagraph body = doc.createParagraph();
            body.createRun().setText("正文段落内容");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);

            ParseSource source = parser.parse(context(out.toByteArray()));

            ParseElement tocElement = source.getElements().stream()
                    .filter(e -> "第一章 总则 ...... 1".equals(e.getText()))
                    .findFirst().orElse(null);
            assertNotNull(tocElement);
            assertTrue(tocElement.getTocCandidate());
            ParseElement bodyElement = source.getElements().stream()
                    .filter(e -> "正文段落内容".equals(e.getText()))
                    .findFirst().orElse(null);
            assertNotNull(bodyElement);
            assertNotEquals(Boolean.TRUE, bodyElement.getTocCandidate());
        }
    }

    @Test
    void docxShouldFillUnitMetrics() throws Exception {
        ParseSource source = parser.parse(context(buildDocx()));

        assertEquals(1, source.getUnitCount());
        PageMetric metric = source.getPageMetrics().getFirst();
        assertTrue(metric.getCharCount() > 0);
        assertEquals(0, metric.getImageCount());
        assertEquals(0.0, metric.getGarbledRatio());
    }

    @Test
    void longDocxShouldSplitUnitsByCharBudget() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText("估".repeat(6001));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);

            ParseSource source = parser.parse(context(out.toByteArray()));

            // Azure 页单位口径：3000 字符 = 1 个判定单元 → 6001 字符 = 3 个单元
            assertEquals(3, source.getUnitCount());
            assertEquals(3, source.getPageMetrics().size());
        }
    }

    @Test
    void imageOnlyDocxShouldReportImageUnit() throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFRun run = doc.createParagraph().createRun();
            run.addPicture(new ByteArrayInputStream(pngBytes()), XWPFDocument.PICTURE_TYPE_PNG, "p.png",
                    Units.toEMU(80), Units.toEMU(80));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);

            ParseSource source = parser.parse(context(out.toByteArray()));

            PageMetric metric = source.getPageMetrics().getFirst();
            assertEquals(0, metric.getCharCount());
            assertEquals(1, metric.getImageCount());
        }
    }

    /** 8×8 PNG（图片单元用例的输入） */
    private byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Test
    void supportsShouldMatchDocxOnly() {
        assertTrue(parser.supports(MIME_DOCX));
        assertFalse(parser.supports("application/msword"));
        assertFalse(parser.supports("application/vnd.ms-excel"));
        assertFalse(parser.supports("application/pdf"));
        assertEquals("poi", parser.capabilityName());
        assertEquals("5.4.0", parser.capabilityVersion());
    }
}
