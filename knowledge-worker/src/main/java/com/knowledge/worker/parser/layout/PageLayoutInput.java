package com.knowledge.worker.parser.layout;

import java.util.List;

/**
 * 页面几何输入：字符级与行级共用同一形状 —— 字符级用于找栏沟，行级用于定阅读顺序。
 *
 * @param pageWidth  页宽（pt）
 * @param pageHeight 页高（pt）
 * @param boxes      盒子列表（字符盒或行盒，按页面原有顺序）
 * @author cxxl
 */
public record PageLayoutInput(double pageWidth, double pageHeight, List<Box> boxes) {

    /**
     * 盒子：左上角坐标 + 宽高（y 向下为正，与元素坐标系一致）。
     *
     * @param x      左边界
     * @param y      上边界
     * @param width  宽
     * @param height 高
     */
    public record Box(double x, double y, double width, double height) {
    }
}
