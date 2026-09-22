package com.knowledge.worker.structure.impl.craft;
import cn.hutool.core.util.ObjectUtil;
import com.knowledge.common.domain.structure.ContinuationJudgeContext;
import com.knowledge.common.domain.structure.ContinuationJudgeResult;
import com.knowledge.common.domain.structure.DocumentRelation;
import com.knowledge.common.domain.structure.ElementBBox;
import com.knowledge.common.domain.structure.UnifiedElement;
import com.knowledge.common.enums.structure.ElementExtensionKey;
import com.knowledge.common.enums.structure.RelationType;
import com.knowledge.common.enums.structure.UnifiedElementType;
import com.knowledge.common.utils.TextUtil;
import com.knowledge.worker.structure.AssembleContext;
import com.knowledge.worker.structure.craft.ContinuationOutcome;
import com.knowledge.worker.structure.craft.TableContinuationResolver;
import com.knowledge.worker.structure.impl.StructureJudgeRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 跨页续表接续实现（仅 PDF 表格）：
 * 主规则四条件：相邻页 + 页尾/页首 + 表头一致 + 列数相同；
 * 放宽规则：列数相同 + 列宽模式一致（表头不一致/无表头）→ 疑似接续（标告警，模型兜底可介入）；
 * 第二页自带表头 = 两张独立表（主规则表头一致除外）。
 *
 * @author cxxl
 */
@Component
@RequiredArgsConstructor
public class TableContinuationResolverImpl implements TableContinuationResolver {

    private final StructureJudgeRegistry judgeRegistry;

    @Override
    public ContinuationOutcome joinContinuations(List<UnifiedElement> ordered, AssembleContext context) {
        ContinuationOutcome outcome = new ContinuationOutcome();
        List<UnifiedElement> elements = new ArrayList<>(ordered);

        for (int i = 0; i < elements.size() - 1; i++) {
            UnifiedElement a = elements.get(i);
            UnifiedElement b = elements.get(i + 1);
            if (isNotPdfTable(a) || isNotPdfTable(b) || !isCutAtPageBottom(a)) {
                continue;
            }
            if (ObjectUtil.isNull(b.getPage()) || ObjectUtil.isNull(a.getPage())
                    || b.getPage() != a.getPage() + 1) {
                continue;
            }
            if (ObjectUtil.isNull(b.getBbox()) || b.getBbox().getY() >= context.getProperties().getPageTopThreshold()) {
                continue; // 下一页表格不在页首
            }
            if (!Objects.equals(a.getCols(), b.getCols())) {
                continue; // 列数不同
            }

            boolean headerSimilar = headerSimilarity(a, b) >= context.getProperties().getContinuationHeaderSimilarity();
            boolean merged;
            boolean suspected = false;
            if (headerSimilar) {
                merged = true; // 主规则
            } else {
                boolean widthPattern = columnWidthPatternMatch(a, b, context);
                if (widthPattern) {
                    // 放宽规则：疑似接续；模型兜底可介入，无实现按规则接续
                    var judge = judgeRegistry.active();
                    if (ObjectUtil.isNotNull(judge)) {
                        ContinuationJudgeResult result = judge.judgeContinuation(judgeContext(a, b));
                        merged = ObjectUtil.isNull(result) || Boolean.TRUE.equals(result.getIsContinuation());
                    } else {
                        merged = true;
                    }
                    suspected = true;
                } else {
                    merged = false;
                }
            }
            if (!merged) {
                continue;
            }

            mergeTables(a, b);
            outcome.getRelations().add(new DocumentRelation(RelationType.CONTINUATION_OF.name(),
                    a.getId() + "#p" + b.getPage(), a.getId() + "#p" + a.getPage(),
                    (suspected ? "疑似续表（放宽规则）" : "续表四条件命中") + "，表头继承"));
            elements.remove(i + 1);
            outcome.setContinuationCount(outcome.getContinuationCount() + 1);
            if (suspected) {
                outcome.setSuspectedCount(outcome.getSuspectedCount() + 1);
            }
        }
        outcome.setElements(elements);
        return outcome;
    }

    private void mergeTables(UnifiedElement a, UnifiedElement b) {
        // 表头继承：B 的表头行（row=0）是重复表头，丢弃；B 其余行拼接到 A
        List<UnifiedElement> mergedCells = new ArrayList<>(ObjectUtil.isNull(a.getCells()) ? List.of() : a.getCells());
        if (ObjectUtil.isNotNull(b.getCells())) {
            for (UnifiedElement cell : b.getCells()) {
                if (ObjectUtil.equals(cell.getRow(), b.getHeaderRow())) {
                    continue; // 重复表头行
                }
                cell.setRow(ObjectUtil.isNull(a.getRows())
                        ? cell.getRow() : a.getRows() + cell.getRow() - 1);
                mergedCells.add(cell);
            }
        }
        a.setCells(mergedCells);
        a.setRows(ObjectUtil.isNull(a.getRows()) ? b.getRows()
                : a.getRows() + b.getRows() - 1);
        a.setHeaderInherited(true);
        a.setPageRange(List.of(a.getPage(), b.getPage()));
        List<ElementBBox> bboxes = new ArrayList<>();
        if (ObjectUtil.isNotNull(a.getBbox())) {
            bboxes.add(new ElementBBox(a.getPage(), a.getBbox()));
        }
        if (ObjectUtil.isNotNull(b.getBbox())) {
            bboxes.add(new ElementBBox(b.getPage(), b.getBbox()));
        }
        a.setBboxes(bboxes);
        a.setBbox(null);
        a.setPage(null);
    }

    /** 是否非 PDF 表格（非 TABLE 类型或无页码；接续判定只针对 PDF 表格） */
    private boolean isNotPdfTable(UnifiedElement element) {
        return !UnifiedElementType.TABLE.name().equals(element.getType())
                || !ObjectUtil.isNotNull(element.getPage());
    }

    private boolean isCutAtPageBottom(UnifiedElement element) {
        Object flag = ObjectUtil.isNull(element.getExtension()) ? null
                : element.getExtension().get(ElementExtensionKey.CUT_AT_PAGE_BOTTOM.key());
        return Boolean.TRUE.equals(flag);
    }

    private double headerSimilarity(UnifiedElement a, UnifiedElement b) {
        List<String> headerA = rowTexts(a, a.getHeaderRow());
        List<String> headerB = rowTexts(b, b.getHeaderRow());
        return TextUtil.jaccardCharSet(String.join("", headerA), String.join("", headerB));
    }

    private List<String> rowTexts(UnifiedElement table, Integer row) {
        return rowCells(table, row).stream().map(UnifiedElement::getText).toList();
    }

    /** 指定行单元格：按 row 过滤 + 按 col 排序（rowTexts/rowWidths 共用口径） */
    private List<UnifiedElement> rowCells(UnifiedElement table, Integer row) {
        if (ObjectUtil.isNull(table.getCells()) || ObjectUtil.isNull(row)) {
            return List.of();
        }
        return table.getCells().stream()
                .filter(c -> ObjectUtil.equals(c.getRow(), row))
                .sorted(Comparator.comparingInt(c -> ObjectUtil.defaultIfNull(c.getCol(), 0)))
                .toList();
    }

    private boolean columnWidthPatternMatch(UnifiedElement a, UnifiedElement b, AssembleContext context) {
        List<Double> widthsA = rowWidths(a, a.getHeaderRow());
        List<Double> widthsB = rowWidths(b, b.getHeaderRow());
        if (widthsA.size() != widthsB.size() || widthsA.isEmpty()) {
            return false;
        }
        double tolerance = context.getProperties().getContinuationColumnWidthTolerance();
        int matched = 0;
        for (int i = 0; i < widthsA.size(); i++) {
            if (Math.abs(widthsA.get(i) - widthsB.get(i)) <= tolerance) {
                matched++;
            }
        }
        return (double) matched / widthsA.size() >= 0.6;
    }

    private List<Double> rowWidths(UnifiedElement table, Integer row) {
        return rowCells(table, row).stream()
                .map(c -> ObjectUtil.isNull(c.getBbox()) ? 0d : c.getBbox().getWidth())
                .toList();
    }

    private ContinuationJudgeContext judgeContext(UnifiedElement a, UnifiedElement b) {
        ContinuationJudgeContext context = new ContinuationJudgeContext();
        context.setTableAHeaders(rowTexts(a, a.getHeaderRow()));
        context.setTableAColumns(a.getCols());
        context.setTableBHeaders(rowTexts(b, b.getHeaderRow()));
        context.setTableBColumns(b.getCols());
        context.setPageA(a.getPage());
        context.setPageB(b.getPage());
        return context;
    }
}
