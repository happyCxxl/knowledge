package com.knowledge.worker.parser.pdf.assemble;

import cn.hutool.core.util.StrUtil;
import com.knowledge.common.domain.parse.BBox;
import com.knowledge.common.domain.parse.ParseElement;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.domain.parse.Provenance;
import com.knowledge.common.enums.parse.ElementType;
import com.knowledge.common.utils.NullUtil;
import com.knowledge.worker.parser.ParseProperties;
import com.knowledge.worker.parser.pdf.detect.TableCandidateDetector;
import com.knowledge.worker.parser.pdf.extract.LineTexts;
import com.knowledge.worker.parser.pdf.extract.PdfGeometry;
import com.knowledge.worker.parser.pdf.model.PageContent;
import com.knowledge.worker.parser.pdf.model.PageLine;
import com.knowledge.worker.parser.pdf.model.Token;

import java.util.ArrayList;
import java.util.List;

/**
 * 无边框表（文本对齐路径）：列对齐聚类（支持度过滤）成表，格内文本按列区间归属；
 * 列对齐失败或空白率过高时出 TABLE 事实并把块降级为段落。
 *
 * @author cxxl
 */
public final class TextTableBuilder {

    private TextTableBuilder() {
    }

    /** 文本路径结算：列对齐聚类成表；失败或空白率过高则降级为段落 */
    static void flush(List<PageLine> block, PageContent page, List<ParseElement> elements,
                      ParseSource source, String fileId, ParseProperties properties) {
        if (block.size() < 2) {
            elements.add(PdfBodyAssembler.toParagraphElement(block, fileId));
            return;
        }
        List<List<Token>> tokenMatrix = block.stream().map(PageLine::tokens).toList();
        int minSupportRows = Math.max(2,
                (int) Math.ceil(block.size() * properties.getTableColumnSupportRatio()));
        List<Double> columnX = TableCandidateDetector.supportedColumns(tokenMatrix, minSupportRows);
        if (columnX.size() < TableCandidateDetector.MIN_TOKENS
                || !TableCandidateDetector.enoughLinesCoverColumns(tokenMatrix, columnX)) {
            TableSupport.degrade(block, page, elements, source, fileId, "列对齐聚类失败（疑似无边框/复杂表格）");
            return;
        }
        // 每行列边界：行左边界 + 内部列起点 + 行右边界（覆盖整行，列对齐外的字符不丢）
        List<List<Double>> rowBoundaries = new ArrayList<>();
        List<List<String>> rowTexts = new ArrayList<>();
        for (PageLine line : block) {
            List<Double> boundaries = new ArrayList<>();
            boundaries.add(line.x());
            for (int i = 1; i < columnX.size(); i++) {
                boundaries.add(columnX.get(i));
            }
            boundaries.add(line.x() + line.width());
            rowBoundaries.add(boundaries);
            rowTexts.add(cellTexts(line, boundaries));
        }
        if (TableSupport.blankTable(rowTexts, properties)) {
            TableSupport.degrade(block, page, elements, source, fileId, "空白率过高（疑似框线/表单区域）");
            return;
        }
        Integer headerRow = detectHeaderRow(rowTexts, block.stream().map(PageLine::fontSize).toList(),
                properties.getTableHeaderMaxNumericRatio());
        ParseElement table = ParseElement.of("t" + page.pageNo() + "_" + Integer.toHexString(block.hashCode()),
                ElementType.TABLE);
        table.setPage(page.pageNo());
        BBox bounds = PdfGeometry.boundsOf(block, PageLine::x, PageLine::y, PageLine::width, PageLine::height);
        table.setBbox(bounds);
        table.setRows(block.size());
        table.setCols(columnX.size());
        table.setHeaderRow(headerRow);
        table.setCutAtPageBottom(PdfGeometry.cutAtPageBottom(bounds, page.pageHeight()));
        List<ParseElement> cells = new ArrayList<>();
        for (int r = 0; r < block.size(); r++) {
            PageLine line = block.get(r);
            List<Double> boundaries = rowBoundaries.get(r);
            List<String> texts = rowTexts.get(r);
            for (int c = 0; c < columnX.size(); c++) {
                ParseElement cell = ParseElement.of("t" + page.pageNo() + "_" + r + "_" + c, ElementType.TABLE_CELL);
                cell.setText(texts.get(c));
                cell.setRow(r);
                cell.setCol(c);
                cell.setIsHeader(NullUtil.isNotNull(headerRow) && r == headerRow);
                // 单元格 bbox：列 x 范围 × 行带高度（组装环节续表列宽模式放宽规则输入）
                double cellX = boundaries.get(c);
                double cellWidth = boundaries.get(c + 1) - cellX;
                cell.setBbox(new BBox(cellX, line.y(), Math.max(cellWidth, 0), line.height()));
                cell.setProvenance(new Provenance(fileId,
                        "pdf#page(" + page.pageNo() + ")/table[0]/cell[" + r + "," + c + "]"));
                cells.add(cell);
            }
        }
        table.setCells(cells);
        elements.add(table);
    }

    /** 行内按边界切格：格内字符重建为文本（边界覆盖整行） */
    private static List<String> cellTexts(PageLine line, List<Double> boundaries) {
        List<String> texts = new ArrayList<>();
        for (int i = 0; i + 1 < boundaries.size(); i++) {
            double from = boundaries.get(i);
            double to = boundaries.get(i + 1);
            texts.add(LineTexts.lineText(line.chars().stream()
                    .filter(ch -> ch.x() >= from && ch.x() < to)
                    .toList()));
        }
        return texts;
    }

    /** 表头行判定：首行含数字占比不超上限，且表体含数字（或首行字号大于表体最大字号） */
    public static Integer detectHeaderRow(List<List<String>> rowTexts, List<Double> rowFontSizes,
                                   double maxNumericRatio) {
        if (rowTexts.size() < 2) {
            return null;
        }
        if (numericRatio(rowTexts.getFirst()) > maxNumericRatio) {
            return null;
        }
        if (rowTexts.stream().skip(1).anyMatch(cells -> numericRatio(cells) > 0)) {
            return 0;
        }
        double firstSize = rowFontSizes.getFirst();
        double bodyMax = rowFontSizes.stream().skip(1).mapToDouble(Double::doubleValue).max().orElse(0);
        return firstSize > bodyMax * 1.05 ? 0 : null;
    }

    /** 行内单元格含数字的比例 */
    private static double numericRatio(List<String> cells) {
        if (cells.isEmpty()) {
            return 0;
        }
        long numeric = cells.stream()
                .filter(text -> StrUtil.isNotBlank(text) && text.matches(".*\\d.*"))
                .count();
        return (double) numeric / cells.size();
    }
}
