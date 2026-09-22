package com.knowledge.worker.parser.impl.parsers;
import com.knowledge.common.domain.input.FileReference;
import com.knowledge.common.domain.parse.ParseElement;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.enums.parse.ElementType;
import com.knowledge.worker.parser.ParseContext;
import com.knowledge.worker.parser.ParseProperties;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.List;

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

            XWPFTable table = doc.createTable(2, 3);
            table.getRow(0).getCell(0).setText("评分项");
            // (0,1) 横向合并 (0,2)
            table.getRow(0).getCell(1).getCTTc().addNewTcPr().addNewGridSpan().setVal(BigInteger.valueOf(2));
            table.getRow(0).getCell(2).setText("被合并");
            table.getRow(1).getCell(0).setText("A1 报价");
            table.getRow(1).getCell(1).setText("低于基准价 1% 以内得 30 分");
            table.getRow(1).getCell(2).setText("30");

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
        assertEquals(3, table.getCols());
        assertEquals(0, table.getHeaderRow());
        // 5 个单元格：(0,0)(0,1 合并跨 2 列)(1,0)(1,1)(1,2)
        assertEquals(5, table.getCells().size());
        ParseElement merged = table.getCells().stream()
                .filter(c -> c.getRow() == 0 && c.getCol() == 1)
                .findFirst().orElse(null);
        assertNotNull(merged);
        assertEquals(2, merged.getColSpan());
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
    void supportsShouldMatchDocxOnly() {
        assertTrue(parser.supports(MIME_DOCX));
        assertFalse(parser.supports("application/msword"));
        assertFalse(parser.supports("application/vnd.ms-excel"));
        assertFalse(parser.supports("application/pdf"));
        assertEquals("poi", parser.capabilityName());
        assertEquals("5.4.0", parser.capabilityVersion());
    }
}
