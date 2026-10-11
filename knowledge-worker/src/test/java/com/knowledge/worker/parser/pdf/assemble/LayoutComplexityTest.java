package com.knowledge.worker.parser.pdf.assemble;

import com.knowledge.worker.parser.ParseProperties;
import com.knowledge.worker.parser.pdf.model.PageLine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 复杂页判定单测：行起点按容差聚成簇，至少两行支撑的簇才算一栏，栏数达到门槛判复杂页。
 *
 * @author cxxl
 */
class LayoutComplexityTest {

    /** 三栏各两行（容差内小抖动算同栏）→ 三栏即复杂页 */
    @Test
    void threeColumnsWithTwoLinesEachShouldBeComplex() {
        List<PageLine> lines = List.of(
                line(72, 100), line(74, 80), line(300, 100), line(302, 80), line(520, 100), line(521, 80));

        assertEquals(3, LayoutComplexity.columnCount(lines, 12.0));
        assertTrue(LayoutComplexity.isComplex(lines, new ParseProperties()));
    }

    /** 两栏 → 未达门槛，不算复杂页（几何口径优先） */
    @Test
    void twoColumnsShouldNotBeComplex() {
        List<PageLine> lines = List.of(line(72, 100), line(73, 80), line(300, 100), line(301, 80));

        assertEquals(2, LayoutComplexity.columnCount(lines, 12.0));
        assertFalse(LayoutComplexity.isComplex(lines, new ParseProperties()));
    }

    /** 只有一行支撑的起点不计栏：缩进行不制造假栏 */
    @Test
    void singleLineStartsShouldNotCountAsColumn() {
        List<PageLine> lines = new ArrayList<>(List.of(line(72, 100), line(72, 80)));
        lines.add(line(120, 60));

        assertEquals(1, LayoutComplexity.columnCount(lines, 12.0));
        assertFalse(LayoutComplexity.isComplex(lines, new ParseProperties()));
    }

    /** 门槛可配：默认门槛 3，改成 2 后两栏页即复杂页 */
    @Test
    void thresholdShouldBeConfigurable() {
        List<PageLine> lines = List.of(line(72, 100), line(72, 80), line(300, 100), line(300, 80));
        ParseProperties properties = new ParseProperties();
        properties.setLayoutModelMinColumns(2);

        assertTrue(LayoutComplexity.isComplex(lines, properties));
    }

    private PageLine line(double x, double y) {
        return new PageLine(1, x, y, 100, 12, "text", 10, "Helvetica", false, List.of(), List.of(), false);
    }
}
