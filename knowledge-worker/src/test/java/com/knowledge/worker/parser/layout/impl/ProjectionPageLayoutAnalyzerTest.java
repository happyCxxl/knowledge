package com.knowledge.worker.parser.layout.impl;

import com.knowledge.worker.parser.layout.LayoutProperties;
import com.knowledge.worker.parser.layout.PageLayoutInput;
import com.knowledge.worker.parser.layout.PageLayoutResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 投影法分栏单测：栏沟检测（竖直覆盖口径）、页级与栏级门槛、跨栏行分带排序。
 *
 * @author cxxl
 */
class ProjectionPageLayoutAnalyzerTest {

    /** 单测用小阈值：页级 20 字符、栏级 10 字符 */
    private static final LayoutProperties PROPERTIES = new LayoutProperties(10.0, 0.25, 0.1, 10, 20, 4);

    private final ProjectionPageLayoutAnalyzer analyzer = new ProjectionPageLayoutAnalyzer();

    @Test
    void shouldFindSingleGutterForTwoColumns() {
        List<PageLayoutInput.Box> chars = new ArrayList<>(column(72, 4, 10));
        chars.addAll(column(320, 4, 10));

        List<Double> gutters = analyzer.gutters(input(chars), PROPERTIES);

        assertEquals(1, gutters.size(), () -> "gutters=" + gutters);
        assertEquals(295.0, gutters.getFirst(), 2.0);
    }

    @Test
    void shouldReturnNoGutterForSingleColumn() {
        assertTrue(analyzer.gutters(input(column(72, 4, 12)), PROPERTIES).isEmpty());
    }

    @Test
    void shouldRejectGutterWhenColumnTooThin() {
        List<PageLayoutInput.Box> chars = new ArrayList<>(column(72, 4, 12));
        chars.addAll(column(320, 4, 1));

        assertTrue(analyzer.gutters(input(chars), PROPERTIES).isEmpty());
    }

    @Test
    void shouldReturnNoGutterWhenPageHasTooFewChars() {
        List<PageLayoutInput.Box> chars = new ArrayList<>(column(72, 2, 2));
        chars.addAll(column(320, 2, 2));

        assertTrue(analyzer.gutters(input(chars), PROPERTIES).isEmpty());
    }

    @Test
    void shouldKeepGutterWhenSpanningHeadingCoversIt() {
        List<PageLayoutInput.Box> chars = new ArrayList<>(column(72, 4, 10));
        chars.addAll(column(320, 4, 10));
        // 页顶跨栏标题横跨栏沟，但竖直覆盖很小
        chars.addAll(spanning(72, 40, 200));

        List<Double> gutters = analyzer.gutters(input(chars), PROPERTIES);

        assertEquals(1, gutters.size(), () -> "gutters=" + gutters);
    }

    @Test
    void shouldIgnoreRaggedEdgeRun() {
        List<PageLayoutInput.Box> chars = new ArrayList<>(column(72, 4, 10));
        chars.addAll(line(330, 100, 12));
        chars.addAll(line(330, 130, 8));
        chars.addAll(line(330, 160, 10));
        chars.addAll(line(330, 190, 10));

        List<Double> gutters = analyzer.gutters(input(chars), PROPERTIES);

        // 只认真实中缝，行尾参差形成的窄带不认
        assertEquals(1, gutters.size(), () -> "gutters=" + gutters);
        assertEquals(300.0, gutters.getFirst(), 2.0);
    }

    @Test
    void shouldOrderByColumnWithinBand() {
        List<PageLayoutInput.Box> lines = List.of(
                box(72, 100, 200, 10), box(72, 120, 200, 10),
                box(320, 100, 200, 10), box(320, 120, 200, 10));

        PageLayoutResult result = analyzer.order(input(lines), List.of(295.0));

        assertEquals(List.of(0, 1, 2, 3), indexes(result));
        assertEquals(List.of(0, 0, 1, 1), columns(result));
    }

    @Test
    void shouldStartNewBandAtSpanningLine() {
        List<PageLayoutInput.Box> lines = List.of(
                box(72, 100, 440, 10), box(72, 140, 200, 10), box(320, 140, 200, 10),
                box(72, 200, 440, 10), box(72, 240, 200, 10), box(320, 240, 200, 10));

        PageLayoutResult result = analyzer.order(input(lines), List.of(295.0));

        assertEquals(List.of(0, 1, 2, 3, 4, 5), indexes(result));
        assertEquals(List.of(-1, 0, 1, -1, 0, 1), columns(result));
    }

    @Test
    void shouldKeepVerticalOrderWhenNoGutter() {
        List<PageLayoutInput.Box> lines = List.of(
                box(72, 120, 200, 10), box(72, 100, 200, 10), box(320, 110, 200, 10));

        PageLayoutResult result = analyzer.order(input(lines), List.of());

        assertEquals(List.of(1, 2, 0), indexes(result));
        assertEquals(List.of(0, 0, 0), columns(result));
    }

    /** 一栏字符：每行 charsPerLine 个（字宽 18pt、间距 20pt），行距 30pt，自 y=100 起 */
    private static List<PageLayoutInput.Box> column(double x, int lines, int charsPerLine) {
        List<PageLayoutInput.Box> boxes = new ArrayList<>();
        for (int row = 0; row < lines; row++) {
            for (int i = 0; i < charsPerLine; i++) {
                boxes.add(new PageLayoutInput.Box(x + i * 20, 100 + row * 30, 18, 10));
            }
        }
        return boxes;
    }

    /** 一行字符：自 x 起按 20pt 间距写 charsPerLine 个（字宽 18pt） */
    private static List<PageLayoutInput.Box> line(double x, double y, int charsPerLine) {
        List<PageLayoutInput.Box> boxes = new ArrayList<>();
        for (int i = 0; i < charsPerLine; i++) {
            boxes.add(new PageLayoutInput.Box(x + i * 20, y, 18, 10));
        }
        return boxes;
    }

    /** 一行字符：自 x 起横向连续覆盖 width（字宽 9pt、间距 10pt） */
    private static List<PageLayoutInput.Box> spanning(double x, double y, double width) {
        List<PageLayoutInput.Box> boxes = new ArrayList<>();
        for (double offset = 0; offset + 9 <= width; offset += 10) {
            boxes.add(new PageLayoutInput.Box(x + offset, y, 9, 10));
        }
        return boxes;
    }

    private static PageLayoutInput input(List<PageLayoutInput.Box> boxes) {
        return new PageLayoutInput(595, 842, boxes);
    }

    private static PageLayoutInput.Box box(double x, double y, double width, double height) {
        return new PageLayoutInput.Box(x, y, width, height);
    }

    private static List<Integer> indexes(PageLayoutResult result) {
        return result.placed().stream().map(PageLayoutResult.Placed::index).toList();
    }

    private static List<Integer> columns(PageLayoutResult result) {
        return result.placed().stream().map(PageLayoutResult.Placed::column).toList();
    }
}
