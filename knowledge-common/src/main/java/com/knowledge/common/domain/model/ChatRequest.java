package com.knowledge.common.domain.model;

import lombok.Data;

import java.util.List;

/**
 * 对话模态入参：模型名 + 消息列表 + 输出约束。
 *
 * @author cxxl
 */
@Data
public class ChatRequest {

    /** 厂商侧模型名（kb_model.model_key） */
    private String modelKey;

    /** 消息列表 */
    private List<ChatMessage> messages;

    /** 输出约束 */
    private OutputSpec output;

    /** 单次超时（毫秒，可空 = 适配器默认） */
    private Integer timeoutMs;
}
