package com.knowledge.worker.structure.impl.craft;
import com.knowledge.common.enums.structure.UnifiedElementType;

import com.knowledge.common.domain.parse.BBox;
import com.knowledge.common.domain.structure.UnifiedElement;
import com.knowledge.worker.structure.AssembleContext;
import com.knowledge.worker.structure.StructureProperties;
import com.knowledge.worker.structure.craft.ContinuationOutcome;
import com.knowledge.worker.structure.impl.StructureJudgeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 跨页续表接续单测：四条件主规则 / 放宽规则疑似 / 非相邻页不接续。
 *
 * @author cxxl
 */
class TableContinuationResolverImplTest {

    private TableContinuationResolverImpl resolver;
    private StructureProperties properties;

    @BeforeEach
    void setUp() {
        properties = new StructureProperties();
        resolver = new TableContinuationResolverImpl(new StructureJudgeRegistry(new ArrayList<>(), properties));
    }

    private AssembleContext context() {
        AssembleContext context = new AssembleContext();
        context.setProperties(properties);
        return context;
    }

    private UnifiedElement table(int page, double y, boolean cutAtBottom, String... headerTexts) {
        UnifiedElement table = new UnifiedElement();
        table.setId("t-" + page + "-" + Math.abs(java.util.Arrays.hashCode(headerTexts)));
        table.setType(UnifiedElementType.TABLE.name());
        table.setPage(page);
        table.setBbox(new BBox(72, y, 400, 100));
        table.setCols(headerTexts.length);
        table.setHeaderRow(0);
        table.setExtension(Map.of("cutAtPageBottom", cutAtBottom));
        List<UnifiedElement> cells = new ArrayList<>();
        for (int i = 0; i < headerTexts.length; i++) {
            UnifiedElement cell = new UnifiedElement();
            cell.setId("tc-" + page + "-" + i);
            cell.setType(UnifiedElementType.TABLE_CELL.name());
            cell.setRow(0);
            cell.setCol(i);
            cell.setIsHeader(true);
            cell.setText(headerTexts[i]);
            cell.setBbox(new BBox(72 + i * 100, y, 100, 20));
            cells.add(cell);
        }
        table.setCells(cells);
        table.setRows(2);
        return table;
    }

    @Test
    void fourConditionsShouldMergeAndInheritHeader() {
        UnifiedElement a = table(2, 700, true, "评分项", "评分标准", "分值");
        UnifiedElement b = table(3, 20, false, "评分项", "评分标准", "分值");

        ContinuationOutcome outcome = resolver.joinContinuations(new ArrayList<>(List.of(a, b)), context());

        assertEquals(1, outcome.getElements().size());
        assertEquals(1, outcome.getContinuationCount());
        assertEquals(0, outcome.getSuspectedCount());
        UnifiedElement merged = outcome.getElements().getFirst();
        assertTrue(merged.getHeaderInherited());
        assertEquals(List.of(2, 3), merged.getPageRange());
        assertEquals(2, merged.getBboxes().size());
        assertTrue(outcome.getRelations().stream().anyMatch(r -> "CONTINUATION_OF".equals(r.getType())));
    }

    @Test
    void relaxedRuleShouldMergeAsSuspected() {
        UnifiedElement a = table(2, 700, true, "评分项", "评分标准", "分值");
        UnifiedElement b = table(3, 20, false, "B1 售后承诺", "质保期不少于 3 年", "10");

        ContinuationOutcome outcome = resolver.joinContinuations(new ArrayList<>(List.of(a, b)), context());

        assertEquals(1, outcome.getElements().size());
        assertEquals(1, outcome.getContinuationCount());
        assertEquals(1, outcome.getSuspectedCount());
        UnifiedElement merged = outcome.getElements().getFirst();
        assertTrue(merged.getHeaderInherited());
    }

    @Test
    void nonAdjacentPagesShouldNotMerge() {
        UnifiedElement a = table(2, 700, true, "评分项", "评分标准", "分值");
        UnifiedElement b = table(5, 20, false, "评分项", "评分标准", "分值");

        ContinuationOutcome outcome = resolver.joinContinuations(new ArrayList<>(List.of(a, b)), context());

        assertEquals(2, outcome.getElements().size());
        assertEquals(0, outcome.getContinuationCount());
    }
}
