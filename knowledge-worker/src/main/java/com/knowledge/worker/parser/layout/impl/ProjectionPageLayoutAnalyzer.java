package com.knowledge.worker.parser.layout.impl;

import com.knowledge.worker.parser.layout.LayoutGeometry;
import com.knowledge.worker.parser.layout.LayoutProperties;
import com.knowledge.worker.parser.layout.PageLayoutAnalyzer;
import com.knowledge.worker.parser.layout.PageLayoutInput;
import com.knowledge.worker.parser.layout.PageLayoutResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;

/**
 * 投影法分栏实现：字符横向覆盖做成直方图，其中竖直覆盖比例低于门槛、且宽度达标的横向带算栏沟；
 * 跨栏行（横跨栏沟的行）把页面切成上下若干带，带内跨栏行在前、其余按栏序与栏内纵向顺序。
 * 单栏页（无栏沟）返回纵向顺序，与不启用分栏时一致。
 *
 * @author cxxl
 */
@Component
public class ProjectionPageLayoutAnalyzer implements PageLayoutAnalyzer {

    /** 覆盖直方图分箱宽度（pt） */
    private static final double BIN_WIDTH = 1.0;

    @Override
    public List<Double> gutters(PageLayoutInput chars, LayoutProperties properties) {
        List<PageLayoutInput.Box> boxes = chars.boxes().stream()
                .filter(box -> box.width() > 0 && box.height() > 0)
                .toList();
        if (boxes.size() < properties.pageMinChars()) {
            return List.of();
        }
        double left = boxes.stream().mapToDouble(PageLayoutInput.Box::x).min().orElse(0);
        double right = boxes.stream().mapToDouble(box -> box.x() + box.width()).max().orElse(0);
        double top = boxes.stream().mapToDouble(PageLayoutInput.Box::y).min().orElse(0);
        double bottom = boxes.stream().mapToDouble(box -> box.y() + box.height()).max().orElse(0);
        int binCount = (int) Math.ceil((right - left) / BIN_WIDTH);
        int rowCount = (int) Math.ceil((bottom - top) / BIN_WIDTH);
        if (binCount < 2 || rowCount < 1) {
            return List.of();
        }
        BitSet[] coveredRows = new BitSet[binCount];
        for (PageLayoutInput.Box box : boxes) {
            int from = Math.max((int) Math.floor((box.x() - left) / BIN_WIDTH), 0);
            int to = Math.min((int) Math.ceil((box.x() + box.width() - left) / BIN_WIDTH), binCount);
            int rowFrom = Math.max((int) Math.floor((box.y() - top) / BIN_WIDTH), 0);
            int rowTo = Math.min((int) Math.ceil((box.y() + box.height() - top) / BIN_WIDTH), rowCount);
            for (int i = from; i < to; i++) {
                if (coveredRows[i] == null) {
                    coveredRows[i] = new BitSet(rowCount);
                }
                coveredRows[i].set(rowFrom, rowTo);
            }
        }
        List<double[]> runs = lowCoverageRuns(coveredRows, rowCount, left, properties);
        if (runs.isEmpty()) {
            return List.of();
        }
        // 候选沟按宽度从大到小逐个试：加进去不会产生过细的栏才接受（行尾参差会形成窄假沟）
        List<double[]> candidates = runs.stream()
                .sorted(Comparator.comparingDouble((double[] run) -> run[1]).reversed())
                .toList();
        List<Double> accepted = new ArrayList<>();
        for (double[] candidate : candidates) {
            if (accepted.size() >= Math.max(properties.maxColumns() - 1, 1)) {
                break;
            }
            List<Double> trial = new ArrayList<>(accepted);
            trial.add(candidate[0] + candidate[1] / 2);
            trial.sort(Double::compareTo);
            if (columnsAcceptable(boxes, trial, properties)) {
                accepted = trial;
            }
        }
        return accepted;
    }

    @Override
    public PageLayoutResult order(PageLayoutInput lines, List<Double> gutters) {
        List<PageLayoutInput.Box> boxes = lines.boxes();
        List<Integer> byY = new ArrayList<>();
        for (int i = 0; i < boxes.size(); i++) {
            byY.add(i);
        }
        byY.sort(Comparator.comparingDouble(index -> boxes.get(index).y()));
        List<PageLayoutResult.Placed> placed = new ArrayList<>();
        List<Integer> band = new ArrayList<>();
        for (int index : byY) {
            if (LayoutGeometry.crossesAny(boxes.get(index), gutters)) {
                flushBand(placed, band, boxes, gutters);
                band = new ArrayList<>();
            }
            band.add(index);
        }
        flushBand(placed, band, boxes, gutters);
        return new PageLayoutResult(placed, gutters);
    }

    /** 结算一带：跨栏行按纵向顺序在前，其余按栏序、栏内纵向顺序 */
    private static void flushBand(List<PageLayoutResult.Placed> placed, List<Integer> band,
                                 List<PageLayoutInput.Box> boxes, List<Double> gutters) {
        band.stream()
                .filter(index -> LayoutGeometry.crossesAny(boxes.get(index), gutters))
                .sorted(Comparator.comparingDouble(index -> boxes.get(index).y()))
                .forEach(index -> placed.add(new PageLayoutResult.Placed(index, -1)));
        band.stream()
                .filter(index -> !LayoutGeometry.crossesAny(boxes.get(index), gutters))
                .sorted(Comparator
                        .comparingInt((Integer index) -> LayoutGeometry.columnOf(boxes.get(index), gutters))
                        .thenComparingDouble(index -> boxes.get(index).y()))
                .forEach(index -> placed.add(new PageLayoutResult.Placed(
                        index, LayoutGeometry.columnOf(boxes.get(index), gutters))));
    }

    /** 竖直覆盖比例低于门槛的连续横向箱成段，段宽达门槛即候选栏沟（返回 {起点 x, 宽度}） */
    private static List<double[]> lowCoverageRuns(BitSet[] coveredRows, int rowCount, double left,
                                                  LayoutProperties properties) {
        List<double[]> runs = new ArrayList<>();
        int start = -1;
        for (int i = 0; i <= coveredRows.length; i++) {
            boolean low = i < coveredRows.length
                    && coverage(coveredRows[i], rowCount) <= properties.gutterMaxCoverage();
            if (low && start < 0) {
                start = i;
            } else if (!low && start >= 0) {
                double width = (i - start) * BIN_WIDTH;
                if (width >= properties.gutterMinWidth()) {
                    runs.add(new double[] {left + start * BIN_WIDTH, width});
                }
                start = -1;
            }
        }
        return runs;
    }

    /** 该横向箱被覆盖的纵向范围占比 */
    private static double coverage(BitSet covered, int rowCount) {
        return covered == null || rowCount == 0 ? 0 : (double) covered.cardinality() / rowCount;
    }

    /** 每栏字符数与占比达门槛才算成立 */
    private static boolean columnsAcceptable(List<PageLayoutInput.Box> boxes, List<Double> gutters,
                                             LayoutProperties properties) {
        int[] counts = new int[gutters.size() + 1];
        for (PageLayoutInput.Box box : boxes) {
            int column = LayoutGeometry.columnOf(box, gutters);
            if (column >= 0) {
                counts[column]++;
            }
        }
        for (int count : counts) {
            if (count < properties.columnMinChars()
                    || (double) count / boxes.size() < properties.columnMinShare()) {
                return false;
            }
        }
        return true;
    }
}
