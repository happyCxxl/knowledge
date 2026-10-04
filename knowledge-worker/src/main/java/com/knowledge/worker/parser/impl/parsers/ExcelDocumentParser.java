package com.knowledge.worker.parser.impl.parsers;

import cn.hutool.core.util.StrUtil;
import com.knowledge.common.domain.parse.ParseElement;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.domain.parse.Provenance;
import com.knowledge.common.enums.parse.ElementType;
import com.knowledge.common.enums.input.FileFormat;
import com.knowledge.common.utils.NullUtil;
import com.knowledge.worker.parser.ParseContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Picture;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Shape;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Excel（XLS/XLSX）原生结构解析器（POI 路径）。
 * 每个 sheet 产出 TABLE 元素；定位 = sheet + 行列（无页码概念）；
 * 合并区域 origin 单元格记 span、其余跳过；公式格取缓存值。
 *
 * @author cxxl
 */
@Slf4j
@Component
public class ExcelDocumentParser extends AbstractPoiDocumentParser {

    @Override
    public boolean supports(String mimeType) {
        return FileFormat.XLS.getMimeType().equals(mimeType)
                || FileFormat.XLSX.getMimeType().equals(mimeType);
    }

    /** Excel 主流程：逐 sheet 产出 TABLE 元素（定位 = sheet + 行列，无页码概念）。 */
    @Override
    protected void parseNative(ParseSource source, byte[] data, ParseContext context) {
        String fileId = context.getFileRef().getFileId();
        boolean xlsx = FileFormat.XLSX.getMimeType().equals(context.getFileRef().getMimeType());
        try (Workbook workbook = xlsx ? new XSSFWorkbook(new ByteArrayInputStream(data))
                : new HSSFWorkbook(new ByteArrayInputStream(data))) {
            DataFormatter formatter = new DataFormatter();
            // 公式格回落取缓存值时按缓存结果类型格式化，避免落成公式串
            formatter.setUseCachedValuesForFormulaCells(true);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            int sheetCount = workbook.getNumberOfSheets();
            List<String> sheetTexts = new ArrayList<>();
            List<Integer> sheetImages = new ArrayList<>();
            for (int s = 0; s < sheetCount; s++) {
                ParseElement tableElement = toSheetTableElement(workbook, formatter, evaluator, s, fileId);
                source.getElements().add(tableElement);
                sheetTexts.add(tableText(tableElement));
                sheetImages.add(pictureCount(workbook.getSheetAt(s)));
            }
            fillUnitMetrics(source, sheetTexts, sheetImages);
        } catch (Exception e) {
            log.warn("Excel 解析失败, fileId={}", fileId, e);
            throw new IllegalStateException("Excel 解析失败: " + e.getMessage(), e);
        }
    }

    /** 单 sheet 表格元素：合并区域 origin 单元格记 span、其余跳过；取值走 cellText。 */
    private ParseElement toSheetTableElement(Workbook workbook, DataFormatter formatter,
                                             FormulaEvaluator evaluator, int sheetIndex, String fileId) {
        Sheet sheet = workbook.getSheetAt(sheetIndex);
        String sheetName = sheet.getSheetName();
        ParseElement tableElement = ParseElement.of("sheet" + sheetIndex, ElementType.TABLE);
        tableElement.setPage(sheetIndex);
        tableElement.setSheetName(sheetName);
        // 合并区域：origin 单元格记 span，其余跳过
        Map<Long, int[]> mergeSpans = new HashMap<>();
        java.util.Set<Long> covered = new java.util.HashSet<>();
        for (CellRangeAddress region : sheet.getMergedRegions()) {
            int rowSpan = region.getLastRow() - region.getFirstRow() + 1;
            int colSpan = region.getLastColumn() - region.getFirstColumn() + 1;
            mergeSpans.put(key(region.getFirstRow(), region.getFirstColumn()), new int[]{rowSpan, colSpan});
            for (int r = region.getFirstRow(); r <= region.getLastRow(); r++) {
                for (int c = region.getFirstColumn(); c <= region.getLastColumn(); c++) {
                    if (r != region.getFirstRow() || c != region.getFirstColumn()) {
                        covered.add(key(r, c));
                    }
                }
            }
        }
        int firstRow = sheet.getFirstRowNum();
        int lastRow = sheet.getLastRowNum();
        boolean hasData = false;
        for (int r = firstRow; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (NullUtil.isNull(row)) {
                continue;
            }
            for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
                if (covered.contains(key(r, c))) {
                    continue;
                }
                Cell cell = row.getCell(c);
                if (NullUtil.isNull(cell)) {
                    continue;
                }
                String text = cellText(cell, formatter, evaluator);
                if (StrUtil.isBlank(text)) {
                    continue;
                }
                hasData = true;
                ParseElement cellElement = ParseElement.of("s" + sheetIndex + "c" + r + "_" + c,
                        ElementType.TABLE_CELL);
                cellElement.setText(text);
                cellElement.setRow(r);
                cellElement.setCol(c);
                cellElement.setIsHeader(r == firstRow);
                int[] span = mergeSpans.get(key(r, c));
                if (NullUtil.isNotNull(span)) {
                    cellElement.setRowSpan(span[0] > 1 ? span[0] : null);
                    cellElement.setColSpan(span[1] > 1 ? span[1] : null);
                }
                cellElement.setProvenance(new Provenance(fileId,
                        "office#sheet[" + sheetName + "]/cell[" + r + "," + c + "]"));
                appendCell(tableElement, cellElement);
            }
        }
        tableElement.setRows(hasData ? lastRow - firstRow + 1 : 0);
        tableElement.setCols(hasData ? (int) sheet.getRow(sheet.getFirstRowNum()).getLastCellNum() : 0);
        tableElement.setHeaderRow(hasData ? 0 : null);
        return tableElement;
    }

    /**
     * 单元格取值：普通格走 DataFormatter（含日期/百分比等显示格式）；
     * 公式格先求值，求值不可用（未支持函数等）时回落到文件里的缓存值，两者都没有则留空（不落公式串）。
     */
    private String cellText(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (!CellType.FORMULA.equals(cell.getCellType())) {
            return formatter.formatCellValue(cell);
        }
        String evaluated = evaluate(cell, formatter, evaluator);
        if (StrUtil.isNotBlank(evaluated)) {
            return evaluated;
        }
        return hasCachedValue(cell) ? formatter.formatCellValue(cell) : evaluated;
    }

    /** 缓存结果是否在文件里：xlsx 看 <v> 元素（缺失时 POI 的缓存类型仍报数值，取出来是 0）；xls 的公式记录总带数值缓存 */
    private static boolean hasCachedValue(Cell cell) {
        return !(cell instanceof XSSFCell xssfCell) || xssfCell.getCTCell().isSetV();
    }

    /** 公式求值（求值失败留空，由 cellText 回落缓存值） */
    private String evaluate(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        try {
            return formatter.formatCellValue(cell, evaluator);
        } catch (RuntimeException e) {
            log.debug("Excel 公式求值失败, sheet={}, cell={}", cell.getSheet().getSheetName(),
                    cell.getAddress(), e);
            return "";
        }
    }

    /** sheet 已产出单元格的文本（单元指标输入） */
    private static String tableText(ParseElement tableElement) {
        return NullUtil.isNull(tableElement.getCells()) ? "" : tableElement.getCells().stream()
                .map(ParseElement::getText)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining("\n"));
    }

    /** sheet 内嵌图片数（图形层里的图片形状；图表不计） */
    private static int pictureCount(Sheet sheet) {
        Drawing<?> drawing = sheet.getDrawingPatriarch();
        if (NullUtil.isNull(drawing)) {
            return 0;
        }
        int count = 0;
        for (Shape shape : drawing) {
            if (shape instanceof Picture) {
                count++;
            }
        }
        return count;
    }

    /** 行列坐标 → 单键（高 32 位行、低 32 位列）。 */
    private long key(int row, int col) {
        return ((long) row << 32) | (col & 0xFFFFFFFFL);
    }
}
