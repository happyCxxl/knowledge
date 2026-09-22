package com.knowledge.common.domain.parse.signal;

import lombok.Data;

/**
 * 页级原始指标（PDF 解析器逐页统计；信号判定器的输入）。
 *
 * @author cxxl
 */
@Data
public class PageMetric {

    /** 页码（从 1 起） */
    private int page;

    /** 非空白字符数 */
    private int charCount;

    /** 乱码率（0~1：非 CJK/ASCII/常用中文标点字符占比） */
    private double garbledRatio;

    /** 文本 bbox 面积 ÷ 页面面积（0~1） */
    private double textAreaRatio;
}
