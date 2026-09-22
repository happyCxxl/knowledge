package com.knowledge.worker.parser;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 解析阈值配置（knowledge.parse 前缀；Nacos 同名键可覆盖）。
 * 阈值为一期初值，待真实样本标定。
 *
 * @author cxxl
 */
@Data
@Component
@ConfigurationProperties(prefix = "knowledge.parse")
public class ParseProperties {

    /** 扫描页判定：单页非空白字符数低于该值 */
    private int scanPageMinChars = 50;

    /** 乱码页判定：非 CJK/ASCII/常用中文标点字符占比 */
    private double garbledRateThreshold = 0.2;

    /** 图片页判定：文本 bbox 面积 ÷ 页面面积 */
    private double textAreaRatioThreshold = 0.05;

    /** 版面异常判定：同一行带内 y 差 ÷ 行高 */
    private double layoutYTolerance = 0.3;

    /** 成功单元占比门槛（≥90% PARTIAL_SUCCESS，<90% FAILED） */
    private double successUnitRatio = 0.9;

    /** 行聚合 y 容差（pt；待样本标定） */
    private double lineYTolerance = 2.0;

    /** 段落聚合：行间距与行高的比值上限（超过则分段；待样本标定） */
    private double paragraphGapRatio = 1.5;

    /** 页眉判定：文本重复出现的最小页数 */
    private int headerMinPages = 2;

    /** 页眉候选区域：页面顶部高度占比 */
    private double headerAreaRatio = 0.08;

    /** 页脚候选区域：页面底部高度占比 */
    private double footerAreaRatio = 0.08;
}
