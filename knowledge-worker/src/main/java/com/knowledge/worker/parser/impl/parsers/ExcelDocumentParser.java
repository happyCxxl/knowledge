package com.knowledge.worker.parser.impl.parsers;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.common.domain.parse.ParseElement;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.domain.parse.Provenance;
import com.knowledge.common.enums.parse.ElementType;
import com.knowledge.common.enums.input.FileFormat;
import com.knowledge.worker.parser.ParseContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Excel（XLS/XLSX）原生结构解析器（POI 路径）。
 * 每个 sheet 产出 TABLE 元素；定位 = sheet + 行列（无页码概念）；
 * 合并区域 origin 单元格记 span、其余跳过；DataFormatter 统一取值。
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

    @Override
    public ParseSource parse(ParseContext context) {
        byte[] data = ParserStreamSupport.readAll(context);
        ParseSource source = ParseSource.nativeSource(capabilityName() + "-" + capabilityVersion());
        String fileId = context.getFileRef().getFileId();
        parseExcel(source, data, fileId, FileFormat.XLSX.getMimeType().equals(context.getFileRef().getMimeType()));
        return source;
    }

    /** Excel 主流程：逐 sheet 产出 TABLE 元素（定位 = sheet + 行列，无页码概念）。 */
    private void parseExcel(ParseSource source, byte[] data, String fileId, boolean xlsx) {
        try (Workbook workbook = xlsx ? new XSSFWorkbook(new ByteArrayInputStream(data))
                : new HSSFWorkbook(new ByteArrayInputStream(data))) {
            DataFormatter formatter = new DataFormatter();
            int sheetCount = workbook.getNumberOfSheets();
            for (int s = 0; s < sheetCount; s++) {
                source.getElements().add(toSheetTableElement(workbook, formatter, s, fileId));
            }
            source.setUnitCount(sheetCount);
        } catch (Exception e) {
            log.warn("Excel 解析失败, fileId={}", fileId, e);
            throw new IllegalStateException("Excel 解析失败: " + e.getMessage(), e);
        }
    }

    /** 单 sheet 表格元素：合并区域 origin 单元格记 span、其余跳过；DataFormatter 统一取值。 */
    private ParseElement toSheetTableElement(Workbook workbook, DataFormatter formatter, int sheetIndex,
                                             String fileId) {
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
            if (ObjectUtil.isNull(row)) {
                continue;
            }
            for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
                if (covered.contains(key(r, c))) {
                    continue;
                }
                Cell cell = row.getCell(c);
                if (ObjectUtil.isNull(cell)) {
                    continue;
                }
                String text = formatter.formatCellValue(cell);
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
                if (ObjectUtil.isNotNull(span)) {
                    cellElement.setRowSpan(span[0] > 1 ? span[0] : null);
                    cellElement.setColSpan(span[1] > 1 ? span[1] : null);
                }
                cellElement.setProvenance(new Provenance(fileId,
                        "office#sheet[" + sheetName + "]/cell[" + r + "," + c + "]"));
                if (tableElement.getCells() == null) {
                    tableElement.setCells(new java.util.ArrayList<>());
                }
                tableElement.getCells().add(cellElement);
            }
        }
        tableElement.setRows(hasData ? lastRow - firstRow + 1 : 0);
        tableElement.setCols(hasData ? (int) sheet.getRow(sheet.getFirstRowNum()).getLastCellNum() : 0);
        tableElement.setHeaderRow(hasData ? 0 : null);
        return tableElement;
    }

    /** 行列坐标 → 单键（高 32 位行、低 32 位列）。 */
    private long key(int row, int col) {
        return ((long) row << 32) | (col & 0xFFFFFFFFL);
    }
}
