package com.knowledge.worker.parser.layout;

import java.util.List;

/**
 * 页面阅读顺序结果。
 *
 * @param placed  按阅读顺序排列的行位置（下标指回输入的 boxes）
 * @param gutters 判定出的栏沟中心 x（按 x 升序；空表示单栏）
 * @author cxxl
 */
public record PageLayoutResult(List<Placed> placed, List<Double> gutters) {

    /**
     * 行位置：输入下标 + 栏号。
     *
     * @param index  输入 boxes 的下标
     * @param column 栏号（0 起；-1 表示跨栏行）
     */
    public record Placed(int index, int column) {
    }
}
