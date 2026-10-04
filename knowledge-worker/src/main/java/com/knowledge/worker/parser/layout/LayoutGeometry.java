package com.knowledge.worker.parser.layout;

import java.util.List;

/**
 * 分栏几何助手：跨沟判定与栏号计算（解析器切行与端口实现排序共用同一口径）。
 *
 * @author cxxl
 */
public final class LayoutGeometry {

    private LayoutGeometry() {
    }

    /** 横向范围是否横跨栏沟（沟中心落在 [x, x+width) 内） */
    public static boolean crosses(double x, double width, double gutter) {
        return x < gutter && x + width > gutter;
    }

    /** 横向范围是否横跨任一栏沟 */
    public static boolean crossesAny(double x, double width, List<Double> gutters) {
        return gutters.stream().anyMatch(gutter -> crosses(x, width, gutter));
    }

    /** 中心落在第几栏（横跨栏沟返回 -1） */
    public static int columnOf(double x, double width, List<Double> gutters) {
        if (crossesAny(x, width, gutters)) {
            return -1;
        }
        double center = x + width / 2;
        int column = 0;
        for (double gutter : gutters) {
            if (center > gutter) {
                column++;
            }
        }
        return column;
    }

    /** 盒子是否横跨栏沟 */
    public static boolean crosses(PageLayoutInput.Box box, double gutter) {
        return crosses(box.x(), box.width(), gutter);
    }

    /** 盒子是否横跨任一栏沟 */
    public static boolean crossesAny(PageLayoutInput.Box box, List<Double> gutters) {
        return crossesAny(box.x(), box.width(), gutters);
    }

    /** 盒子中心落在第几栏（横跨栏沟返回 -1） */
    public static int columnOf(PageLayoutInput.Box box, List<Double> gutters) {
        return columnOf(box.x(), box.width(), gutters);
    }
}
