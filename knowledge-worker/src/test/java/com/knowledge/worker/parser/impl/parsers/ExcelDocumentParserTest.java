package com.knowledge.worker.parser.impl.parsers;
import com.knowledge.common.domain.input.FileReference;
import com.knowledge.common.domain.parse.ParseElement;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.enums.parse.ElementType;
import com.knowledge.worker.parser.ParseContext;
import com.knowledge.worker.parser.ParseProperties;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.imageio.ImageIO;

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

    /** 全部表格单元格文本 */
    private List<String> cellTexts(ParseSource source) {
        return source.getElements().stream()
                .filter(e -> ElementType.TABLE.name().equals(e.getType()))
                .flatMap(e -> e.getCells().stream())
                .map(ParseElement::getText).toList();
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
    void formulaCellShouldUseCachedValueInsteadOfFormulaText() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("S1");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("项目");
            header.createCell(1).setCellValue("金额");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("合计");
            Cell formula = row.createCell(1);
            formula.setCellFormula("SUM(2,3)");
            // 算一次写入缓存值，模拟 Excel 保存时的显示值
            workbook.getCreationHelper().createFormulaEvaluator().evaluateFormulaCell(formula);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            ParseSource source = parser.parse(context(out.toByteArray(), MIME_XLSX));

            assertTrue(cellTexts(source).contains("5"), () -> "cells=" + cellTexts(source));
            assertTrue(cellTexts(source).stream().noneMatch(text -> text.contains("SUM")),
                    () -> "cells=" + cellTexts(source));
        }
    }

    @Test
    void formulaWithoutCachedValueShouldBeEvaluated() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("S1");
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue("合计");
            row.createCell(1).setCellFormula("1+2");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            ParseSource source = parser.parse(context(out.toByteArray(), MIME_XLSX));

            assertTrue(cellTexts(source).contains("3"), () -> "cells=" + cellTexts(source));
        }
    }

    @Test
    void unsupportedFormulaShouldFallBackToCachedValue() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("S1");
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue("合计");
            Cell formula = row.createCell(1);
            // POI 求值不支持 WEBSERVICE，文件里的缓存值 7 是唯一可用来源
            formula.setCellFormula("WEBSERVICE(\"http://example.com\")");
            formula.setCellValue(7);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            ParseSource source = parser.parse(context(out.toByteArray(), MIME_XLSX));

            assertTrue(cellTexts(source).contains("7"), () -> "cells=" + cellTexts(source));
            assertTrue(cellTexts(source).stream().noneMatch(text -> text.contains("WEBSERVICE")),
                    () -> "cells=" + cellTexts(source));
        }
    }

    @Test
    void excelShouldFillSheetMetricsIncludingEmptySheet() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet first = workbook.createSheet("有数据");
            Row header = first.createRow(0);
            header.createCell(0).setCellValue("项目");
            header.createCell(1).setCellValue("金额");
            workbook.createSheet("空表");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            ParseSource source = parser.parse(context(out.toByteArray(), MIME_XLSX));

            // 每 worksheet = 1 个判定单元；空 sheet 单元字符数为 0
            assertEquals(2, source.getUnitCount());
            assertTrue(source.getPageMetrics().get(0).getCharCount() > 0);
            assertEquals(0, source.getPageMetrics().get(1).getCharCount());
            assertEquals(0, source.getPageMetrics().get(1).getImageCount());
        }
    }

    @Test
    void sheetPictureShouldBeCountedInMetrics() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("带图");
            sheet.createRow(0).createCell(0).setCellValue("见图");
            int pictureIndex = workbook.addPicture(pngBytes(), Workbook.PICTURE_TYPE_PNG);
            XSSFDrawing drawing = (XSSFDrawing) sheet.createDrawingPatriarch();
            drawing.createPicture(new XSSFClientAnchor(0, 0, 0, 0, 1, 1, 3, 5), pictureIndex);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            ParseSource source = parser.parse(context(out.toByteArray(), MIME_XLSX));

            assertEquals(1, source.getPageMetrics().getFirst().getImageCount());
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
    void supportsShouldMatchExcelFormats() {
        assertTrue(parser.supports(MIME_XLS));
        assertTrue(parser.supports(MIME_XLSX));
        assertFalse(parser.supports("application/msword"));
        assertFalse(parser.supports("application/pdf"));
        assertEquals("poi", parser.capabilityName());
        assertEquals("5.4.0", parser.capabilityVersion());
    }
}
