package com.knowledge.worker.chunking.impl.table;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.common.domain.chunk.Chunk;
import com.knowledge.common.domain.preprocess.ViewCell;
import com.knowledge.common.domain.preprocess.ViewElement;
import com.knowledge.common.domain.structure.UnifiedElement;
import com.knowledge.common.enums.chunk.ChunkContentType;
import com.knowledge.worker.chunking.SliceContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 表格切片公共支撑：单元格文本索引 / 表头收集（空文本不算表头）/ 数据行准备 /
 * Markdown 表格渲染 / 行级切片分组 / Chunk 构建。表格路四个算法（行级/行组/整表/表+引导段）共用。
 *
 * @author cxxl
 */
public final class TableMarkdownSupport {

    private TableMarkdownSupport() {
    }

    /** 行数据 + 与行对齐的来源单元格 ID */
    public static final class TableRows {
        public final List<Map<Integer, String>> rows;
        public final List<List<String>> ids;

        TableRows(List<Map<Integer, String>> rows, List<List<String>> ids) {
            this.rows = rows;
            this.ids = ids;
        }
    }

    /** 表格切片准备结果（结构表 + 表头映射 + 数据行）。 */
    public record TablePrep(UnifiedElement table, Map<Integer, String> headerByCol, TableRows rows) {
    }

    /** 表格切片准备：结构表 + 表头映射 + 数据行（无可切单元格返回 null）。 */
    public static TablePrep prepare(ViewElement element, SliceContext context) {
        UnifiedElement table = context.getById().get(element.getElementId());
        if (ObjectUtil.isNull(table) || ObjectUtil.isNull(table.getCells()) || table.getCells().isEmpty()) {
            return null;
        }
        Map<String, String> textByCell = textByCell(element);
        Map<Integer, String> headerByCol = headerByCol(table, textByCell);
        TableRows prepared = prepareRows(table, textByCell);
        return new TablePrep(table, headerByCol, prepared);
    }

    /** 视图单元格检索文本索引（cellId → normalizedText） */
    public static Map<String, String> textByCell(ViewElement element) {
        Map<String, String> map = new HashMap<>();
        if (ObjectUtil.isNotNull(element.getCells())) {
            for (ViewCell cell : element.getCells()) {
                if (StrUtil.isNotBlank(cell.getCellId())) {
                    map.put(cell.getCellId(), cell.getNormalizedText());
                }
            }
        }
        return map;
    }

    /** 表头（isHeader 单元格，按列；空文本不算表头） */
    public static Map<Integer, String> headerByCol(UnifiedElement table, Map<String, String> textByCell) {
        Map<Integer, String> headerByCol = new HashMap<>();
        for (UnifiedElement cell : table.getCells()) {
            if (Boolean.TRUE.equals(cell.getIsHeader()) && ObjectUtil.isNotNull(cell.getCol())) {
                String headerText = cellText(cell, textByCell);
                if (StrUtil.isNotBlank(headerText)) {
                    headerByCol.putIfAbsent(cell.getCol(), headerText);
                }
            }
        }
        return headerByCol;
    }

    /** 数据行（非表头单元格按行分组，按行序；空白单元格跳过，全空行跳过） */
    public static TableRows prepareRows(UnifiedElement table, Map<String, String> textByCell) {
        Map<Integer, List<UnifiedElement>> grouped = new LinkedHashMap<>();
        for (UnifiedElement cell : table.getCells()) {
            if (Boolean.TRUE.equals(cell.getIsHeader()) || ObjectUtil.isNull(cell.getRow())) {
                continue;
            }
            grouped.computeIfAbsent(cell.getRow(), k -> new ArrayList<>()).add(cell);
        }
        List<Map<Integer, String>> rows = new ArrayList<>();
        List<List<String>> ids = new ArrayList<>();
        for (Integer rowNumber : grouped.keySet().stream().sorted().toList()) {
            List<UnifiedElement> cells = grouped.get(rowNumber).stream()
                    .sorted(Comparator.comparingInt(c -> ObjectUtil.defaultIfNull(c.getCol(), 0)))
                    .toList();
            Map<Integer, String> byCol = new LinkedHashMap<>();
            List<String> rowIds = new ArrayList<>();
            for (UnifiedElement cell : cells) {
                String value = cellText(cell, textByCell);
                if (StrUtil.isNotBlank(value)) {
                    byCol.put(cell.getCol(), value);
                }
                rowIds.add(cell.getId());
            }
            if (byCol.isEmpty()) {
                continue;
            }
            rows.add(byCol);
            ids.add(rowIds);
        }
        return new TableRows(rows, ids);
    }

    /** 列集：有表头用表头列；否则用行内出现过的列（均按列序） */
    public static List<Integer> columns(Map<Integer, String> headerByCol, List<Map<Integer, String>> rows) {
        List<Integer> columns = new ArrayList<>();
        if (!headerByCol.isEmpty()) {
            columns.addAll(headerByCol.keySet());
        } else {
            Set<Integer> columnSet = new HashSet<>();
            rows.forEach(row -> columnSet.addAll(row.keySet()));
            columns.addAll(columnSet);
        }
        columns.sort(Integer::compareTo);
        return columns;
    }

    /** 行组 → Markdown 表格内容（有表头：表头行 + 分隔行 + 数据行；无表头：仅数据行） */
    public static String markdownContent(List<Map<Integer, String>> groupRows, Map<Integer, String> headerByCol) {
        List<Integer> columns = columns(headerByCol, groupRows);
        List<String> lines = new ArrayList<>();
        if (!headerByCol.isEmpty()) {
            lines.add(markdownRow(columns.stream().map(headerByCol::get).toList()));
            lines.add(markdownSeparator(columns.size()));
        }
        for (Map<Integer, String> row : groupRows) {
            lines.add(markdownRow(columns.stream().map(row::get).toList()));
        }
        return String.join("\n", lines);
    }

    /** 单元格列表 → Markdown 行（管道转义与换行折叠见 sanitizeCell） */
    public static String markdownRow(List<String> cells) {
        return "| " + cells.stream().map(TableMarkdownSupport::sanitizeCell)
                .collect(Collectors.joining(" | ")) + " |";
    }

    /** Markdown 表头分隔行（列数下限 1） */
    public static String markdownSeparator(int columns) {
        return "|" + "---|".repeat(Math.max(1, columns));
    }

    /** 单元格文本净化：管道转义 + 换行折叠为空格 + trim（空值返回空串） */
    public static String sanitizeCell(String value) {
        if (StrUtil.isBlank(value)) {
            return "";
        }
        return value.replace("|", "\\|").replaceAll("[\\r\\n]+", " ").trim();
    }

    /** 行级切片：短行（< groupThreshold）每 groupSize 行一组，长行单行成片（表头随片） */
    public static List<Chunk> rowSlice(TableRows prepared, Map<Integer, String> headerByCol,
                                       ViewElement element, UnifiedElement table, SliceContext context,
                                       int groupThreshold, int groupSize) {
        List<Chunk> chunks = new ArrayList<>();
        List<Map<Integer, String>> groupBuffer = new ArrayList<>();
        List<List<String>> groupIds = new ArrayList<>();
        for (int i = 0; i < prepared.rows.size(); i++) {
            Map<Integer, String> row = prepared.rows.get(i);
            int rowLength = row.values().stream().mapToInt(String::length).sum();
            if (rowLength < groupThreshold) {
                groupBuffer.add(row);
                groupIds.add(prepared.ids.get(i));
                if (groupBuffer.size() >= groupSize) {
                    chunks.add(buildChunk(markdownContent(groupBuffer, headerByCol), element, table, context, groupIds));
                    groupBuffer = new ArrayList<>();
                    groupIds = new ArrayList<>();
                }
            } else {
                if (!groupBuffer.isEmpty()) {
                    chunks.add(buildChunk(markdownContent(groupBuffer, headerByCol), element, table, context, groupIds));
                    groupBuffer = new ArrayList<>();
                    groupIds = new ArrayList<>();
                }
                chunks.add(buildChunk(markdownContent(List.of(row), headerByCol), element, table, context,
                        List.of(prepared.ids.get(i))));
            }
        }
        if (!groupBuffer.isEmpty()) {
            chunks.add(buildChunk(markdownContent(groupBuffer, headerByCol), element, table, context, groupIds));
        }
        return chunks;
    }

    /** 构建表格片（tableRef=视图元素 ID；页码取整表 pageRange/page，来源=行内单元格 ID 并集） */
    public static Chunk buildChunk(String content, ViewElement element, UnifiedElement table,
                                   SliceContext context, List<List<String>> rowIds) {
        Chunk chunk = new Chunk();
        chunk.setContent(content);
        chunk.setContentType(ChunkContentType.TABLE.name());
        chunk.setTitlePath(String.join(" > ", context.getTitlePath()));
        chunk.setTableRef(element.getElementId());
        List<String> sourceIds = new ArrayList<>();
        rowIds.forEach(sourceIds::addAll);
        chunk.setSourceElementIds(sourceIds);
        chunk.setPageRange(pageRangeOf(table));
        chunk.setCharCount(content.length());
        return chunk;
    }

    private static String cellText(UnifiedElement cell, Map<String, String> textByCell) {
        String normalized = textByCell.get(cell.getId());
        return StrUtil.blankToDefault(normalized, cell.getText());
    }

    private static List<Integer> pageRangeOf(UnifiedElement table) {
        if (ObjectUtil.isNotNull(table.getPageRange()) && !table.getPageRange().isEmpty()) {
            return new ArrayList<>(table.getPageRange());
        }
        return ObjectUtil.isNotNull(table.getPage()) ? List.of(table.getPage()) : null;
    }
}
