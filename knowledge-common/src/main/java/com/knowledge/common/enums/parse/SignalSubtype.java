package com.knowledge.common.enums.parse;

/**
 * 信号子类型：信号判定的结构化结论，是管线降级分支的控制流依据。
 * evidence 仅作展示，分支判定一律按本枚举（不依赖文案）。
 *
 * @author cxxl
 */
public enum SignalSubtype {

    /** 扫描页：单页非空白字符数低于阈值 */
    SCANNED,

    /** 乱码页：非 CJK/ASCII 字符占比超阈值 */
    GARBLED,

    /** 文字占比低：文本 bbox 面积占比低于阈值（疑似图片页，仅告警） */
    IMAGE_LOW_RATIO,

    /** 嵌入图片：段落/文档内嵌图片仅引用无文字 */
    IMAGE_EMBEDDED,

    /** 表格规则失败：列对齐聚类失败，区域降级为段落 */
    TABLE_RULE_FAILED,

    /** 版面规则失败：版面异常，按顺序输出 */
    LAYOUT_RULE_FAILED
}
