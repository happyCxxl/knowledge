package com.knowledge.model.adapter;

import com.knowledge.common.domain.model.AuthType;
import com.knowledge.common.domain.model.ChatMessage;
import com.knowledge.common.domain.model.ChatRequest;
import com.knowledge.common.domain.model.GenerationResult;
import com.knowledge.common.domain.model.MessageRole;
import com.knowledge.common.domain.model.OutputFormat;
import com.knowledge.common.domain.model.OutputSpec;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import com.knowledge.common.utils.NullUtil;
import com.knowledge.model.engine.ModelInstanceRegistry;
import com.knowledge.model.listener.ModelCallTrace;
import com.knowledge.model.listener.ModelCallTraceHolder;
import com.knowledge.model.listener.ModelUsageMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.ChatRequestOptions;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonRawSchema;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * OpenAI 兼容适配器：服务所有走该协议的厂商，完成「我们的契约 → LC4j 请求」与「LC4j 响应 → GenerationResult」
 * 双向映射。LC4j 类型出现在 knowledge-model 内部的 `adapter` / `engine` / `invoke` / `listener` 四个包，
 * 业务侧不出现。对话模态已实现，向量与视觉模态沿用 SPI 默认的未支持口径。
 * 调用上下文经请求选项的属性表交给用量监听器。
 *
 * @author cxxl
 */
@Component
@RequiredArgsConstructor
public class OpenAiCompatibleAdapter implements ModelAdapter {

    /** JSON_SCHEMA 直传时的 schema 名（OpenAI 兼容端点要求字母、数字、下划线、中划线） */
    private static final String JSON_SCHEMA_NAME = "output";

    private final ModelInstanceRegistry instanceRegistry;

    private final ModelCallTraceHolder modelCallTraceHolder;

    @Override
    public Set<AuthType> supportedAuthTypes() {
        return Set.of(AuthType.BEARER);
    }

    /**
     * 对话模态：按厂商码与请求里的模型名取实例 → 组装 LC4j 请求 → 调用 → 映射出参。
     *
     * @param vendorCode 厂商码
     * @param request    对话入参（模型/消息/输出约束/超时）
     * @return 原始文本 + 通用树 + 用量 + 模型标识
     */
    @Override
    public GenerationResult chat(String vendorCode, ChatRequest request) {
        ChatModel chatModel = instanceRegistry.chatModel(vendorCode, request.getModelKey());
        ChatResponse response = chatModel.chat(toLc4jRequest(request), requestOptions());
        return toGenerationResult(response, request.getModelKey());
    }

    /** 请求选项：已绑定调用上下文即带上属性表，未绑定取空选项（监听器无从记录，静默跳过） */
    private ChatRequestOptions requestOptions() {
        ModelCallTrace trace = modelCallTraceHolder.current();
        return NullUtil.isNull(trace) ? ChatRequestOptions.EMPTY
                : ChatRequestOptions.builder().addListenerAttribute(ModelCallTrace.ATTRIBUTE_KEY, trace).build();
    }

    /** 契约 → LC4j 请求：消息列表 + 输出约束（TEXT 不设 ResponseFormat） */
    private dev.langchain4j.model.chat.request.ChatRequest toLc4jRequest(ChatRequest request) {
        dev.langchain4j.model.chat.request.ChatRequest.Builder builder =
                dev.langchain4j.model.chat.request.ChatRequest.builder().messages(toLc4jMessages(request.getMessages()));
        ResponseFormat responseFormat = toResponseFormat(request.getOutput());
        if (NullUtil.isNotNull(responseFormat)) {
            builder.responseFormat(responseFormat);
        }
        return builder.build();
    }

    /** 消息列表映射：SYSTEM → SystemMessage、ASSISTANT → AiMessage、USER 与未标注角色 → UserMessage */
    private List<dev.langchain4j.data.message.ChatMessage> toLc4jMessages(List<ChatMessage> messages) {
        List<dev.langchain4j.data.message.ChatMessage> lc4jMessages = new ArrayList<>();
        if (NullUtil.isNull(messages)) {
            return lc4jMessages;
        }
        for (ChatMessage message : messages) {
            lc4jMessages.add(toLc4jMessage(message));
        }
        return lc4jMessages;
    }

    /** 单条消息映射：内容缺失按空串处理 */
    private dev.langchain4j.data.message.ChatMessage toLc4jMessage(ChatMessage message) {
        String content = NullUtil.isNull(message.getContent()) ? "" : message.getContent();
        MessageRole role = message.getRole();
        if (MessageRole.SYSTEM == role) {
            return SystemMessage.from(content);
        }
        if (MessageRole.ASSISTANT == role) {
            return AiMessage.from(content);
        }
        return UserMessage.from(content);
    }

    /**
     * 输出约束映射：TEXT 返回 null（请求不带 ResponseFormat）、JSON_OBJECT 设 JSON、
     * JSON_SCHEMA 设 JSON 加完整 schema；JSON_SCHEMA 缺 schema 即抛「模型请求参数或内容被拒」。
     */
    private ResponseFormat toResponseFormat(OutputSpec output) {
        OutputFormat format = NullUtil.isNull(output) ? OutputFormat.TEXT : output.getFormat();
        if (NullUtil.isNull(format) || OutputFormat.TEXT == format) {
            return null;
        }
        if (OutputFormat.JSON_OBJECT == format) {
            return ResponseFormat.JSON;
        }
        String schema = output.getSchema();
        if (NullUtil.isNull(schema) || schema.isBlank()) {
            throw new KnowledgeException(ErrorCode.MODEL_BAD_REQUEST, "JSON_SCHEMA 输出缺 schema");
        }
        return ResponseFormat.builder()
                .type(ResponseFormatType.JSON)
                .jsonSchema(JsonSchema.builder().name(JSON_SCHEMA_NAME).rootElement(JsonRawSchema.from(schema)).build())
                .build();
    }

    /**
     * 响应映射：content 取响应文本、usage 取 TokenUsage、model 取响应回传值（缺失时回填请求模型名）。
     * modelVersion 该版本响应不带，留 null。
     */
    private GenerationResult toGenerationResult(ChatResponse response, String modelKey) {
        GenerationResult result = new GenerationResult();
        AiMessage aiMessage = response.aiMessage();
        result.setContent(NullUtil.isNull(aiMessage) ? null : aiMessage.text());
        result.setUsage(ModelUsageMapper.toModelUsage(response.tokenUsage()));
        String modelName = response.modelName();
        result.setModel(NullUtil.isNull(modelName) ? modelKey : modelName);
        return result;
    }
}
