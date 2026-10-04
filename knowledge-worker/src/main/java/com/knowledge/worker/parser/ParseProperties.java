package com.knowledge.worker.parser;

import com.knowledge.worker.parser.layout.LayoutProperties;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 解析阈值配置（knowledge.parse 前缀；Nacos 同名键可覆盖）。
 * 阈值为初值，待真实样本标定。
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

    /** 页眉/页脚判定：同文本重复出现的最小页数（按不同页计） */
    private int runningTextMinPages = 2;

    /** 页眉候选区域：页面顶部高度占比 */
    private double headerAreaRatio = 0.08;

    /** 页脚候选区域：页面底部高度占比 */
    private double footerAreaRatio = 0.08;

    /** 线网格：线段需覆盖区域边长的比例（低于该比例的线不参与网格） */
    private double tableLineCoverRatio = 0.6;

    /** 文本列聚类：一列需被多少比例的行支持（低于该比例的列剔除） */
    private double tableColumnSupportRatio = 0.5;

    /** 假表门限：非空单元格占比低于该值即弃表（空白率门禁） */
    private double tableMinFilledRatio = 0.1;

    /** 表头判定：首行含数字的单元格占比上限（超过该值判为无表头） */
    private double tableHeaderMaxNumericRatio = 0.3;

    /** 分栏：栏沟最小宽度（pt；连续低覆盖横向带达到该宽度才算栏沟） */
    private double columnGutterMinWidth = 10.0;

    /** 分栏：栏沟最大竖直覆盖比例（横向带内被字符覆盖的纵向范围占比低于该值才算栏沟） */
    private double columnGutterMaxCoverage = 0.25;

    /** 分栏：每栏最小字符占比（低于该比例不认该栏） */
    private double columnMinShare = 0.1;

    /** 分栏：每栏最少字符数 */
    private int columnMinChars = 10;

    /** 分栏：单页参与判定所需的最少字符数（低于该值不分栏） */
    private int columnMinPageChars = 100;

    /** 分栏：最大栏数 */
    private int columnMaxCount = 4;

    /** 分栏与阅读顺序阈值（版面端口输入） */
    public LayoutProperties layout() {
        return new LayoutProperties(columnGutterMinWidth, columnGutterMaxCoverage, columnMinShare,
                columnMinChars, columnMinPageChars, columnMaxCount);
    }
}
