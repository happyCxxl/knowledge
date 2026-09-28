package com.knowledge.common.dto.response.lineage;

import lombok.Data;

/**
 * 能力快照（执行树节点展示用）。
 *
 * <p>**为什么不直接把 product.capabilitySnapshot 原文给前端**：那一列存的是
 * {@code CapabilitySnapshot} 序列化后的 JSON 文本，透传出去前端只能拿到
 * {@code {"parserName":"pdfbox","parserVersion":"3.0.4"}} 这样的原始串 ——
 * 让前端显示或解析 JSON 都是接口设计错误。这里解析成对象再返回。
 *
 * <p>字段与 {@link com.knowledge.common.domain.parse.CapabilitySnapshot} 一一对应；
 * {@code ocr/layout/table} 为一期预留能力，未接入时三者均为 null（与领域对象口径一致：
 * null 显式表示"未接该能力"，而不是"未知"）。用 {@link CapabilityRefVO} 表达引用，
 * 避免把领域对象直接暴露到接口层。
 *
 * @author cxxl
 */
@Data
public class LineageCapabilityVO {

    /** 原生解析器名称（如 pdfbox） */
    private String parserName;

    /** 原生解析器版本（如 3.0.4） */
    private String parserVersion;

    /** OCR 能力（预留；null = 未接） */
    private CapabilityRefVO ocr;

    /** 版面分析能力（预留；null = 未接） */
    private CapabilityRefVO layout;

    /** 表格识别能力（预留；null = 未接） */
    private CapabilityRefVO table;

    /**
     * 能力引用（模型名 + 版本）。
     */
    @Data
    public static class CapabilityRefVO {

        private String model;

        private String version;
    }
}
