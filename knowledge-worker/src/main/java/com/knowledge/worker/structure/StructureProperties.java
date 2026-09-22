package com.knowledge.worker.structure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 组装阈值配置（knowledge.structure 前缀；Nacos 同名键可覆盖）。
 * 阈值为草案值，待代表性样本标定。
 *
 * @author cxxl
 */
@Data
@Component
@ConfigurationProperties(prefix = "knowledge.structure")
public class StructureProperties {

    /** 去重 IoU 阈值（草案 0.3） */
    private double iouThreshold = 0.3;

    /** 去重文本相似度阈值（Jaccard，草案 0.7） */
    private double textSimilarityThreshold = 0.7;

    /** XY-cut 行带间距 ÷ 行高（草案 1.5） */
    private double xyCutBandGapRatio = 1.5;

    /** XY-cut 块间距（pt，草案 12） */
    private double xyCutBlockGap = 12.0;

    /** 续表表头一致阈值（草案 0.8） */
    private double continuationHeaderSimilarity = 0.8;

    /** 续表放宽规则列宽容差（pt，草案 3） */
    private double continuationColumnWidthTolerance = 3.0;

    /** 页首判定阈值（pt）：表格 bbox.y 低于该值视为页首 */
    private double pageTopThreshold = 50.0;

    /** 标题候选短句长度上限（防编号误判） */
    private int titleCandidateMaxLength = 40;

    /** 重复页判定：两页文本 bigram Jaccard 阈值（草案值 0.95） */
    private double repeatPageJaccard = 0.95;

    /** 重复段判定：文本相似度阈值（草案值 0.9） */
    private double repeatSegmentSimilarity = 0.9;

    /** 重复段判定：段落最小长度（草案值 50 字符） */
    private int repeatSegmentMinLen = 50;

    /** 噪声页判定：页文本乱码率阈值（草案值 0.4） */
    private double noiseGarbledRatio = 0.4;

    /** 模型判断兜底开关（一期关闭） */
    private boolean modelFallbackEnabled = false;
}
