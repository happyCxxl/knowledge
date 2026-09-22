package com.knowledge.common.enums.parse;

/**
 * 质量告警码（落产物 JSON quality.warnings[].code，与详情页/下游环节的展示契约）。
 * 跨环节共享（解析/组装等环节均经 QualityWarning 落告警）。
 *
 * @author cxxl
 */
public enum QualityWarningCode {

    // ---- 解析环节 ----

    /** 乱码页：乱码率超阈值，计失败页 */
    GARBLED_PAGE,

    /** 扫描页：无文本层，OCR 预留一期不支持，计失败页 */
    SCANNED_PAGE,

    /** 嵌入图片文字未识别（OCR 预留，一期仅记录引用与图注），不计失败 */
    IMAGE_TEXT_UNRECOGNIZED,

    /** 疑似图片页：文字占比低于阈值（仅告警，OCR 预留），不计失败 */
    IMAGE_PAGE_SUSPECTED,

    /** 表格规则失败，区域降级为段落，不计失败 */
    TABLE_RULE_FALLBACK,

    /** 版面规则失败，按顺序输出，不计失败 */
    LAYOUT_RULE_FALLBACK,

    // ---- 组装环节 ----

    /** 标题候选：规则拿不准，已按正文处理 */
    TITLE_CANDIDATE,

    /** 疑似续表：放宽规则命中，默认接续 + 表头继承 */
    SUSPECTED_CONTINUATION,

    /** 溯源缺失：部分元素缺原文定位 */
    PROVENANCE_MISSING,

    /** 噪声页：空白/纯图片/乱码页已标记 */
    NOISE_PAGE
}
