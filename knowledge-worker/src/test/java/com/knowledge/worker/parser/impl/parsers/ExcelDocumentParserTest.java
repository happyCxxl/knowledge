package com.knowledge.worker.parser.impl.parsers;
import com.knowledge.common.domain.input.FileReference;
import com.knowledge.common.domain.parse.ParseElement;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.enums.parse.ElementType;
import com.knowledge.worker.parser.ParseContext;
import com.knowledge.worker.parser.ParseProperties;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Excel 原生解析器单测：XLSX 合并区域、XLS 同路径、supports 与能力标识。
 *
 * @author cxxl
 */
class ExcelDocumentParserTest {

    private static final String MIME_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String MIME_XLS = "application/vnd.ms-excel";

    private final ExcelDocumentParser parser = new ExcelDocumentParser();

    private ParseContext context(byte[] data, String mimeType) {
        ParseContext context = new ParseContext();
        FileReference fileRef = new FileReference();
        fileRef.setFileId("F-1");
        fileRef.setFileName("t");
        fileRef.setMimeType(mimeType);
        context.setFileRef(fileRef);
        context.setInputStream(new ByteArrayInputStream(data));
        context.setProperties(new ParseProperties());
        return context;
    }

    private byte[] buildXlsx() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("评分表");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("评分项");
            header.createCell(1).setCellValue("分值");
            Row first = sheet.createRow(1);
            first.createCell(0).setCellValue("A1 报价");
            first.createCell(1).setCellValue(30);
            // A3:A4 纵向合并
            sheet.addMergedRegion(new CellRangeAddress(2, 3, 0, 0));
            Row merged = sheet.createRow(2);
            merged.createCell(0).setCellValue("合并单元格");
            merged.createCell(1).setCellValue(15);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }

    @Test
    void xlsxShouldParseSheetTableWithMerges() throws Exception {
        ParseSource source = parser.parse(context(buildXlsx(), MIME_XLSX));

        assertEquals(1, source.getUnitCount());
        ParseElement table = source.getElements().stream()
                .filter(e -> ElementType.TABLE.name().equals(e.getType()))
                .findFirst().orElse(null);
        assertNotNull(table);
        assertEquals(0, table.getPage());
        ParseElement merged = table.getCells().stream()
                .filter(c -> "合并单元格".equals(c.getText()))
                .findFirst().orElse(null);
        assertNotNull(merged);
        assertEquals(2, merged.getRowSpan());
        assertNull(merged.getColSpan());
        // 表头候选：第一行单元格 isHeader
        ParseElement headerCell = table.getCells().stream()
                .filter(c -> "评分项".equals(c.getText()))
                .findFirst().orElse(null);
        assertNotNull(headerCell);
        assertTrue(headerCell.getIsHeader());
        assertNull(table.getProvenance());
        assertTrue(merged.getProvenance().getPath().contains("sheet[评分表]"));
    }

    @Test
    void xlsShouldParseViaSameWorkbookPath() throws Exception {
        try (HSSFWorkbook workbook = new HSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("S1");
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue("列A");
            row.createCell(1).setCellValue("列B");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            ParseSource source = parser.parse(context(out.toByteArray(), MIME_XLS));
            assertEquals(1, source.getUnitCount());
            assertEquals(1, source.getElements().size());
            assertTrue(source.getElements().getFirst().getCells().size() >= 2);
        }
    }

    @Test
    void supportsShouldMatchExcelFormats() {
        assertTrue(parser.supports(MIME_XLS));
        assertTrue(parser.supports(MIME_XLSX));
        assertFalse(parser.supports("application/msword"));
        assertFalse(parser.supports("application/pdf"));
        assertEquals("poi", parser.capabilityName());
        assertEquals("5.4.0", parser.capabilityVersion());
    }
}
