package com.knowledge.worker.parser.layout;

/**
 * 分栏与阅读顺序判定阈值（由解析阈值映射，见 ParseProperties#layout）。
 *
 * @param gutterMinWidth     栏沟最小宽度（pt）：连续低覆盖的横向带达到该宽度才算栏沟
 * @param gutterMaxCoverage  栏沟最大竖直覆盖比例：横向带内被字符覆盖的纵向范围占比低于该值才算栏沟
 * @param columnMinShare     每栏最小字符占比（低于该比例的栏不认，整页退回单栏）
 * @param columnMinChars     每栏最少字符数
 * @param pageMinChars       单页参与分栏判定所需的最少字符数（低于该值不分栏）
 * @param maxColumns         最大栏数（候选栏沟按宽度取前几名）
 * @author cxxl
 */
public record LayoutProperties(double gutterMinWidth, double gutterMaxCoverage, double columnMinShare,
                               int columnMinChars, int pageMinChars, int maxColumns) {
}
