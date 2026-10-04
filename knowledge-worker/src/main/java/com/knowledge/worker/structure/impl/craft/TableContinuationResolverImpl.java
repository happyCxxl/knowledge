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
import com.knowledge.common.utils.NullUtil;
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
 * 主规则四条件：相邻页 + 页尾/页首 + 表头一致 + 列数相同（相邻性按当前末页比较，同一张表可链式跨任意页数）；
 * 放宽规则：列数相同 + 列宽模式一致（表头不一致/无表头）→ 疑似接续（标告警，模型兜底可介入）；
 * 第二页自带表头 = 两张独立表（主规则表头一致除外）。
 * 合并结果保留首页的 page 与 bbox，跨页位置由 pageRange/bboxes 逐页累积承载。
 *
 * @author cxxl
 */
@Component
@RequiredArgsConstructor
public class TableContinuationResolverImpl implements TableContinuationResolver {

    private static final ContinuationDecision NOT_MERGED = new ContinuationDecision(false, false);

    private static final ContinuationDecision MERGED = new ContinuationDecision(true, false);

    private static final ContinuationDecision SUSPECTED = new ContinuationDecision(true, true);

    private final StructureJudgeRegistry judgeRegistry;

    /** 接续判定结果（是否合并 + 是否属放宽规则的疑似接续） */
    private record ContinuationDecision(boolean merged, boolean suspected) {
    }

    @Override
    public ContinuationOutcome joinContinuations(List<UnifiedElement> ordered, AssembleContext context) {
        ContinuationOutcome outcome = new ContinuationOutcome();
        List<UnifiedElement> elements = new ArrayList<>(ordered);

        for (int i = 0; i < elements.size() - 1; i++) {
            // 链式推进：合并结果留在原位继续与后一条比较，同一张表可跨任意页数
            while (i + 1 < elements.size()) {
                ContinuationDecision decision = decide(elements.get(i), elements.get(i + 1), context);
                if (!decision.merged()) {
                    break;
                }
                UnifiedElement a = elements.get(i);
                UnifiedElement b = elements.get(i + 1);
                int firstPage = firstPageOf(a);
                int nextPage = b.getPage();
                mergeTables(a, b);
                outcome.getRelations().add(new DocumentRelation(RelationType.CONTINUATION_OF.name(),
                        a.getId() + "#p" + nextPage, a.getId() + "#p" + firstPage,
                        (decision.suspected() ? "疑似续表（放宽规则）" : "续表四条件命中") + "，表头继承"));
                elements.remove(i + 1);
                outcome.setContinuationCount(outcome.getContinuationCount() + 1);
                if (decision.suspected()) {
                    outcome.setSuspectedCount(outcome.getSuspectedCount() + 1);
                }
            }
        }
        outcome.setElements(elements);
        return outcome;
    }

    /** 接续判定（四条件 + 表头一致或列宽模式放宽规则）；页码相邻性按当前末页比较 */
    private ContinuationDecision decide(UnifiedElement a, UnifiedElement b, AssembleContext context) {
        if (isNotPdfTable(a) || isNotPdfTable(b) || !isCutAtPageBottom(a)) {
            return NOT_MERGED;
        }
        if (NullUtil.isNull(b.getPage()) || NullUtil.isNull(lastPageOf(a))
                || !Objects.equals(b.getPage(), lastPageOf(a) + 1)) {
            return NOT_MERGED;
        }
        if (NullUtil.isNull(b.getBbox()) || b.getBbox().getY() >= context.getProperties().getPageTopThreshold()) {
            return NOT_MERGED; // 下一页表格不在页首
        }
        if (!Objects.equals(a.getCols(), b.getCols())) {
            return NOT_MERGED; // 列数不同
        }
        if (headerSimilarity(a, b) >= context.getProperties().getContinuationHeaderSimilarity()) {
            return MERGED; // 主规则
        }
        if (!columnWidthPatternMatch(a, b, context)) {
            return NOT_MERGED;
        }
        // 放宽规则：疑似接续；模型兜底可介入，无实现按规则接续
        var judge = judgeRegistry.active();
        if (NullUtil.isNull(judge)) {
            return SUSPECTED;
        }
        ContinuationJudgeResult result = judge.judgeContinuation(judgeContext(a, b));
        return NullUtil.isNull(result) || Boolean.TRUE.equals(result.getIsContinuation()) ? SUSPECTED : NOT_MERGED;
    }

    private void mergeTables(UnifiedElement a, UnifiedElement b) {
        // 表头继承：B 的表头行（row=0）是重复表头，丢弃；B 其余行拼接到 A
        List<UnifiedElement> mergedCells = new ArrayList<>(NullUtil.isNull(a.getCells()) ? List.of() : a.getCells());
        if (NullUtil.isNotNull(b.getCells())) {
            for (UnifiedElement cell : b.getCells()) {
                if (ObjectUtil.equals(cell.getRow(), b.getHeaderRow())) {
                    continue; // 重复表头行
                }
                cell.setRow(NullUtil.isNull(a.getRows())
                        ? cell.getRow() : a.getRows() + cell.getRow() - 1);
                mergedCells.add(cell);
            }
        }
        a.setCells(mergedCells);
        a.setRows(NullUtil.isNull(a.getRows()) ? b.getRows()
                : a.getRows() + b.getRows() - 1);
        a.setHeaderInherited(true);
        // 跨页位置以列表承载：pageRange/bboxes 逐页累积；首页的 page 与 bbox 保留在元素上
        a.setPageRange(appendPageRange(a, b));
        a.setBboxes(appendBboxes(a, b));
    }

    /** 页码范围累积：首次合并写入首页，其后逐页追加（同一页不重复） */
    private List<Integer> appendPageRange(UnifiedElement a, UnifiedElement b) {
        List<Integer> range = new ArrayList<>();
        if (NullUtil.isNotNull(a.getPageRange())) {
            range.addAll(a.getPageRange());
        } else if (NullUtil.isNotNull(a.getPage())) {
            range.add(a.getPage());
        }
        if (NullUtil.isNotNull(b.getPage()) && !range.contains(b.getPage())) {
            range.add(b.getPage());
        }
        return range;
    }

    /** 分段框累积：首页框与各续页框按页序保留 */
    private List<ElementBBox> appendBboxes(UnifiedElement a, UnifiedElement b) {
        List<ElementBBox> bboxes = new ArrayList<>();
        if (NullUtil.isNotNull(a.getBboxes())) {
            bboxes.addAll(a.getBboxes());
        } else if (NullUtil.isNotNull(a.getBbox()) && NullUtil.isNotNull(a.getPage())) {
            bboxes.add(new ElementBBox(a.getPage(), a.getBbox()));
        }
        if (NullUtil.isNotNull(b.getBbox()) && NullUtil.isNotNull(b.getPage())) {
            bboxes.add(new ElementBBox(b.getPage(), b.getBbox()));
        }
        return bboxes;
    }

    /** 首页页码：合并结果的首页在 pageRange 首位，未合并时即 page 本身 */
    private Integer firstPageOf(UnifiedElement element) {
        List<Integer> pageRange = element.getPageRange();
        return NullUtil.isNotNull(pageRange) && !pageRange.isEmpty() ? pageRange.getFirst() : element.getPage();
    }

    /** 末页页码：合并结果的末页在 pageRange 末尾，未合并时即 page 本身 */
    private Integer lastPageOf(UnifiedElement element) {
        List<Integer> pageRange = element.getPageRange();
        return NullUtil.isNotNull(pageRange) && !pageRange.isEmpty() ? pageRange.getLast() : element.getPage();
    }

    /** 是否非 PDF 表格（非 TABLE 类型或无页码；接续判定只针对 PDF 表格） */
    private boolean isNotPdfTable(UnifiedElement element) {
        return !UnifiedElementType.TABLE.name().equals(element.getType())
                || !NullUtil.isNotNull(element.getPage());
    }

    private boolean isCutAtPageBottom(UnifiedElement element) {
        Object flag = NullUtil.isNull(element.getExtension()) ? null
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
        if (NullUtil.isNull(table.getCells()) || NullUtil.isNull(row)) {
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
                .map(c -> NullUtil.isNull(c.getBbox()) ? 0d : c.getBbox().getWidth())
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
