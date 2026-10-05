package com.knowledge.biz.service.support;

import com.knowledge.common.domain.parse.Provenance;
import com.knowledge.common.domain.structure.UnifiedDocument;
import com.knowledge.common.domain.structure.UnifiedElement;
import com.knowledge.common.enums.structure.UnifiedElementType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 组装统计单测：溯源口径（结构性节点不计入分母、表格经由单元格可回溯）与结论文案。
 *
 * @author cxxl
 */
class StructureStatsSupportTest {

    private UnifiedElement element(String id, UnifiedElementType type) {
        UnifiedElement element = new UnifiedElement();
        element.setId(id);
        element.setType(type.name());
        return element;
    }

    private UnifiedElement traced(String id, UnifiedElementType type) {
        UnifiedElement element = element(id, type);
        element.setProvenance(new Provenance("f-1", "pdf#page(1)"));
        return element;
    }

    private Map<String, Object> statsOf(List<UnifiedElement> elements) {
        UnifiedDocument document = new UnifiedDocument();
        document.setElements(elements);
        return StructureStatsSupport.stats(null, null, document);
    }

    @Test
    void excelSectionsShouldNotLowerCoverage() {
        UnifiedElement section = element("s-1", UnifiedElementType.SECTION);
        UnifiedElement table = element("t-1", UnifiedElementType.TABLE);
        UnifiedElement cell = element("tc-1", UnifiedElementType.TABLE_CELL);
        cell.setProvenance(new Provenance("f-1", "office#sheet[评分表]/cell[0,0]"));
        table.setCells(List.of(cell));

        Map<String, Object> stats = statsOf(List.of(section, table));

        assertEquals(1L, stats.get(StructureStatsSupport.KEY_TRACED_COUNT));
        assertEquals(100, stats.get(StructureStatsSupport.KEY_PROVENANCE_COVERAGE));
    }

    @Test
    void tableWithoutProvenanceShouldCountMissing() {
        Map<String, Object> stats = statsOf(List.of(
                traced("n-1", UnifiedElementType.PARAGRAPH), element("t-1", UnifiedElementType.TABLE)));

        assertEquals(1L, stats.get(StructureStatsSupport.KEY_TRACED_COUNT));
        assertEquals(50, stats.get(StructureStatsSupport.KEY_PROVENANCE_COVERAGE));
    }

    @Test
    void mostlyTracedDocumentShouldNotBeStatedLow() {
        List<UnifiedElement> elements = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            elements.add(traced("n-" + i, UnifiedElementType.PARAGRAPH));
        }
        elements.add(element("t-1", UnifiedElementType.TABLE));

        Map<String, Object> stats = statsOf(elements);

        assertEquals(5L, stats.get(StructureStatsSupport.KEY_TRACED_COUNT));
        assertEquals(83, stats.get(StructureStatsSupport.KEY_PROVENANCE_COVERAGE));
        assertFalse(StructureStatsSupport.summary(null, stats, "SUCCESS").contains("溯源偏低"));
    }

    @Test
    void lowCoverageShouldBeStatedInSummary() {
        UnifiedElement table = element("t-1", UnifiedElementType.TABLE);
        table.setCells(List.of(element("tc-1", UnifiedElementType.TABLE_CELL)));

        Map<String, Object> stats = statsOf(List.of(
                element("n-1", UnifiedElementType.PARAGRAPH), table));

        assertEquals(0, stats.get(StructureStatsSupport.KEY_PROVENANCE_COVERAGE));
        assertTrue(StructureStatsSupport.summary(null, stats, "SUCCESS").contains("溯源偏低 0%"));
    }
}
