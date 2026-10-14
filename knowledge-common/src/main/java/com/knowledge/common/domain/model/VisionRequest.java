package com.knowledge.common.domain.model;

import lombok.Data;

/**
 * 视觉模态入参：模型名 + 图像 + 提示词 + 输出约束。
 *
 * @author cxxl
 */
@Data
public class VisionRequest {

    /** 厂商侧模型名（kb_model.model_key） */
    private String modelKey;

    /** 图像引用（对象存储 key / URL，可空） */
    private String imageRef;

    /** 图像字节（与 imageRef 二选一） */
    private byte[] imageBytes;

    /** 区域（可空 = 整图） */
    private ImageRegion region;

    /** 提示词 */
    private String prompt;

    /** 输出约束 */
    private OutputSpec output;

    /** 单次超时（毫秒，可空 = 适配器默认） */
    private Integer timeoutMs;
}
