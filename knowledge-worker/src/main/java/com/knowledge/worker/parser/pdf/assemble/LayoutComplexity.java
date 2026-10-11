package com.knowledge.worker.parser.pdf.assemble;

import com.knowledge.worker.parser.ParseProperties;
import com.knowledge.worker.parser.pdf.model.PageLine;

import java.util.ArrayList;
import java.util.List;

/**
 * 复杂页判定：按行起点横坐标聚类估栏数（同一栏的行起点落在容差内），
 * 栏数达到门槛判复杂页 —— 版面能力只对复杂页发起调用，几何口径仍是默认。
 *
 * @author cxxl
 */
public final class LayoutComplexity {

    private LayoutComplexity() {
    }

    /**
     * 估栏数。
     *
     * @param lines     页面行
     * @param tolerance 行起点横向容差（pt）
     * @return 栏数（行起点聚成的簇数；至少被两行支撑的簇才计一栏）
     */
    public static int columnCount(List<PageLine> lines, double tolerance) {
        List<double[]> clusters = new ArrayList<>();
        List<Integer> supports = new ArrayList<>();
        for (PageLine line : lines) {
            int hit = -1;
            for (int index = 0; index < clusters.size(); index++) {
                if (Math.abs(clusters.get(index)[0] - line.x()) <= tolerance) {
                    hit = index;
                    break;
                }
            }
            if (hit < 0) {
                clusters.add(new double[] {line.x()});
                supports.add(1);
            } else {
                supports.set(hit, supports.get(hit) + 1);
            }
        }
        int columns = 0;
        for (Integer support : supports) {
            if (support >= 2) {
                columns++;
            }
        }
        return columns;
    }

    /**
     * 是否复杂页。
     *
     * @param lines     页面行
     * @param properties 解析阈值
     * @return 栏数达到 `layoutModelMinColumns` 即为复杂页
     */
    public static boolean isComplex(List<PageLine> lines, ParseProperties properties) {
        return columnCount(lines, properties.getLayoutColumnStartTolerance())
                >= properties.getLayoutModelMinColumns();
    }
}
