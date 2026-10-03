package com.knowledge.worker.parser.impl.parsers.pdf;

import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 页面线条收集与线网格：采集页面矢量线段（横线/竖线），按相交关系聚成线框区域，
 * 再由区域内线条求列边界、行边界与分隔线缺失（合并单元格）。
 * 坐标统一为左上原点，与字符 y 同口径。
 *
 * @author cxxl
 */
public final class RuleLines {

    /** 线段（左上原点坐标） */
    public record Segment(double x1, double y1, double x2, double y2) {

        boolean horizontal() {
            return Math.abs(y1 - y2) <= TOLERANCE;
        }

        boolean vertical() {
            return Math.abs(x1 - x2) <= TOLERANCE;
        }

        double length() {
            return Math.max(Math.abs(x1 - x2), Math.abs(y1 - y2));
        }
    }

    /** 线框区域（一组相交线条的外接矩形） */
    public record Region(double left, double top, double right, double bottom) {

        /** 点是否落在区域内（含容差） */
        public boolean contains(double x, double y) {
            return x >= left - TOLERANCE && x <= right + TOLERANCE
                    && y >= top - TOLERANCE && y <= bottom + TOLERANCE;
        }
    }

    /**
     * 线网格：列边界与行边界（含外侧边界）+ 分隔线缺失矩阵。
     * columnGaps[r][c] 为 true 表示第 r 行里列 c 与 c+1 之间无竖线（两格横向合并）；
     * rowGaps[r][c] 为 true 表示第 c 列里行 r 与 r+1 之间无横线（两格纵向合并）。
     */
    public record Grid(List<Double> columns, List<Double> rows,
                       List<List<Boolean>> columnGaps, List<List<Boolean>> rowGaps) {

        public int colCount() {
            return columns.size() - 1;
        }

        public int rowCount() {
            return rows.size() - 1;
        }
    }

    /** 坐标与线位判定容差（pt） */
    private static final double TOLERANCE = 2.0;

    /** 参与网格的最短线段（pt）：滤掉装饰性短线 */
    private static final double MIN_SEGMENT_LENGTH = 3.0;

    /** 单页参与线网格的线段上限：超出即跳过线检测（矢量密集页退回文本表格路径） */
    private static final int MAX_RULE_SEGMENTS = 2000;

    private RuleLines() {
    }

    /** 采集页面线段；页面内容流异常时返回空列表（该页走文本表格路径） */
    public static List<Segment> collect(PDPage page, double pageHeight) {
        Collector collector = new Collector(page, pageHeight);
        try {
            collector.processPage(page);
        } catch (IOException e) {
            return List.of();
        }
        return collector.segments;
    }

    /** 线框区域：相交线条聚成组件，取外接矩形并保留能形成至少 2 列 2 行网格的组件 */
    public static List<Region> regions(List<Segment> segments, double lineCoverRatio) {
        List<Segment> usable = segments.stream()
                .filter(s -> s.length() >= MIN_SEGMENT_LENGTH)
                .toList();
        if (usable.size() > MAX_RULE_SEGMENTS) {
            return List.of();
        }
        List<Region> regions = new ArrayList<>();
        boolean[] visited = new boolean[usable.size()];
        for (int i = 0; i < usable.size(); i++) {
            if (visited[i]) {
                continue;
            }
            List<Segment> component = new ArrayList<>();
            collectComponent(usable, visited, i, component);
            Region region = bounds(component);
            Grid grid = fit(component, region, lineCoverRatio);
            if (grid != null) {
                regions.add(region);
            }
        }
        regions.sort(Comparator.comparingDouble(Region::top));
        return regions;
    }

    /** 区域内求线网格；列或行不足 2 段时返回 null */
    public static Grid fit(List<Segment> segments, Region region, double lineCoverRatio) {
        List<Segment> verticals = fullyInside(segments, region, true, lineCoverRatio);
        List<Segment> horizontals = fullyInside(segments, region, false, lineCoverRatio);
        List<Double> columns = cluster(verticals.stream().map(Segment::x1).toList());
        List<Double> rows = cluster(horizontals.stream().map(Segment::y1).toList());
        if (columns.size() < 3 || rows.size() < 3) {
            return null;
        }
        List<List<Boolean>> columnGaps = new ArrayList<>();
        for (int r = 0; r + 1 < rows.size(); r++) {
            List<Boolean> rowGap = new ArrayList<>();
            for (int c = 1; c + 1 < columns.size(); c++) {
                rowGap.add(!covered(verticals, true, columns.get(c), rows.get(r), rows.get(r + 1), lineCoverRatio));
            }
            columnGaps.add(rowGap);
        }
        List<List<Boolean>> rowGaps = new ArrayList<>();
        for (int r = 1; r + 1 < rows.size(); r++) {
            List<Boolean> gaps = new ArrayList<>();
            for (int c = 0; c + 1 < columns.size(); c++) {
                gaps.add(!covered(horizontals, false, rows.get(r), columns.get(c), columns.get(c + 1), lineCoverRatio));
            }
            rowGaps.add(gaps);
        }
        return new Grid(columns, rows, columnGaps, rowGaps);
    }

    /** 区域内的线：按方向筛出，并要求其长度覆盖区域对应边长的比例 */
    private static List<Segment> fullyInside(List<Segment> segments, Region region, boolean vertical,
                                             double lineCoverRatio) {
        List<Segment> result = new ArrayList<>();
        for (Segment segment : segments) {
            if (vertical != segment.vertical()) {
                continue;
            }
            if (vertical) {
                if (region.left() - TOLERANCE <= segment.x1() && segment.x1() <= region.right() + TOLERANCE
                        && segment.length() >= (region.bottom() - region.top()) * lineCoverRatio) {
                    result.add(segment);
                }
            } else if (region.top() - TOLERANCE <= segment.y1() && segment.y1() <= region.bottom() + TOLERANCE
                    && segment.length() >= (region.right() - region.left()) * lineCoverRatio) {
                result.add(segment);
            }
        }
        return result;
    }

    /** 在给定带内是否存在覆盖比例达标的线 */
    private static boolean covered(List<Segment> segments, boolean vertical, double at,
                                   double bandFrom, double bandTo, double lineCoverRatio) {
        double band = bandTo - bandFrom;
        for (Segment segment : segments) {
            if (vertical != segment.vertical()) {
                continue;
            }
            double position = vertical ? segment.x1() : segment.y1();
            if (Math.abs(position - at) > TOLERANCE) {
                continue;
            }
            double low = vertical ? Math.min(segment.y1(), segment.y2()) : Math.min(segment.x1(), segment.x2());
            double high = vertical ? Math.max(segment.y1(), segment.y2()) : Math.max(segment.x1(), segment.x2());
            double overlap = Math.min(high, bandTo) - Math.max(low, bandFrom);
            if (band <= 0 || overlap >= band * lineCoverRatio) {
                return true;
            }
        }
        return false;
    }

    /** 一维聚类：容差内合并，取均值作代表位 */
    private static List<Double> cluster(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        List<Double> centers = new ArrayList<>();
        double sum = 0;
        int count = 0;
        for (double value : sorted) {
            if (count > 0 && value - sum / count > TOLERANCE) {
                centers.add(sum / count);
                sum = 0;
                count = 0;
            }
            sum += value;
            count++;
        }
        if (count > 0) {
            centers.add(sum / count);
        }
        return centers;
    }

    private static void collectComponent(List<Segment> segments, boolean[] visited, int start,
                                         List<Segment> component) {
        List<Integer> pending = new ArrayList<>();
        pending.add(start);
        visited[start] = true;
        while (!pending.isEmpty()) {
            int index = pending.removeLast();
            Segment current = segments.get(index);
            component.add(current);
            for (int i = 0; i < segments.size(); i++) {
                if (!visited[i] && intersects(current, segments.get(i))) {
                    visited[i] = true;
                    pending.add(i);
                }
            }
        }
    }

    /** 两线相交或共线重叠即视为同一线框组件 */
    private static boolean intersects(Segment a, Segment b) {
        if (a.horizontal() == b.horizontal()) {
            if (a.horizontal()) {
                return Math.abs(a.y1() - b.y1()) <= TOLERANCE
                        && overlap(a.x1(), a.x2(), b.x1(), b.x2()) > TOLERANCE;
            }
            return Math.abs(a.x1() - b.x1()) <= TOLERANCE
                    && overlap(a.y1(), a.y2(), b.y1(), b.y2()) > TOLERANCE;
        }
        Segment horizontal = a.horizontal() ? a : b;
        Segment vertical = a.horizontal() ? b : a;
        double horizontalX1 = Math.min(horizontal.x1(), horizontal.x2());
        double horizontalX2 = Math.max(horizontal.x1(), horizontal.x2());
        double verticalY1 = Math.min(vertical.y1(), vertical.y2());
        double verticalY2 = Math.max(vertical.y1(), vertical.y2());
        return vertical.x1() >= horizontalX1 - TOLERANCE && vertical.x1() <= horizontalX2 + TOLERANCE
                && horizontal.y1() >= verticalY1 - TOLERANCE && horizontal.y1() <= verticalY2 + TOLERANCE;
    }

    private static double overlap(double a1, double a2, double b1, double b2) {
        return Math.min(Math.max(a1, a2), Math.max(b1, b2)) - Math.max(Math.min(a1, a2), Math.min(b1, b2));
    }

    private static Region bounds(List<Segment> segments) {
        double left = segments.stream().mapToDouble(s -> Math.min(s.x1(), s.x2())).min().orElse(0);
        double right = segments.stream().mapToDouble(s -> Math.max(s.x1(), s.x2())).max().orElse(0);
        double top = segments.stream().mapToDouble(s -> Math.min(s.y1(), s.y2())).min().orElse(0);
        double bottom = segments.stream().mapToDouble(s -> Math.max(s.y1(), s.y2())).max().orElse(0);
        return new Region(left, top, right, bottom);
    }

    /** 图形流引擎：把路径操作收集为横竖线段 */
    private static final class Collector extends PDFGraphicsStreamEngine {

        private final List<Segment> segments = new ArrayList<>();
        private final double pageHeight;
        private double startX;
        private double startY;
        private double currentX;
        private double currentY;

        private Collector(PDPage page, double pageHeight) {
            super(page);
            this.pageHeight = pageHeight;
        }

        @Override
        public void appendRectangle(Point2D p0, Point2D p1, Point2D p2, Point2D p3) {
            add(p0, p1);
            add(p1, p2);
            add(p2, p3);
            add(p3, p0);
        }

        @Override
        public void moveTo(float x, float y) {
            startX = x;
            startY = y;
            currentX = x;
            currentY = y;
        }

        @Override
        public void lineTo(float x, float y) {
            add(new Point2D.Double(currentX, currentY), new Point2D.Double(x, y));
            currentX = x;
            currentY = y;
        }

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
            currentX = x3;
            currentY = y3;
        }

        @Override
        public void closePath() {
            add(new Point2D.Double(currentX, currentY), new Point2D.Double(startX, startY));
            currentX = startX;
            currentY = startY;
        }

        @Override
        public Point2D getCurrentPoint() {
            return new Point2D.Double(currentX, currentY);
        }

        @Override
        public void endPath() {
        }

        @Override
        public void strokePath() {
        }

        @Override
        public void fillPath(int windingRule) {
        }

        @Override
        public void fillAndStrokePath(int windingRule) {
        }

        @Override
        public void clip(int windingRule) {
        }

        @Override
        public void drawImage(PDImage pdImage) {
        }

        @Override
        public void shadingFill(COSName shadingName) {
        }

        /** 记一段：转成左上原点；零长与斜线不入网格 */
        private void add(Point2D from, Point2D to) {
            double x1 = from.getX();
            double y1 = pageHeight - from.getY();
            double x2 = to.getX();
            double y2 = pageHeight - to.getY();
            if (Math.abs(x1 - x2) <= TOLERANCE && Math.abs(y1 - y2) <= TOLERANCE) {
                return;
            }
            if (Math.abs(x1 - x2) > TOLERANCE && Math.abs(y1 - y2) > TOLERANCE) {
                return;
            }
            segments.add(new Segment(x1, y1, x2, y2));
        }
    }
}
