package com.knowledge.model.invoke;

import com.knowledge.common.domain.model.ChatRequest;
import com.knowledge.common.domain.model.GenerationResult;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import com.knowledge.common.utils.NullUtil;
import com.knowledge.model.adapter.ModelAdapter;
import com.knowledge.model.listener.ModelCallTrace;
import com.knowledge.model.listener.ModelCallTraceHolder;
import com.knowledge.model.validate.OutputSchemaValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 模型调用器：取适配器执行一次调用，对结果做结构校验后返回。
 * 同一实例内的重试与退避由底层实例承担，本类不另加重试；调用失败的异常在本类映射成错误码，
 * 「是否可降级」由 {@link ModelErrorClassifier} 当场判定并经 {@link #canFallback(Throwable)} 暴露。
 * 调用上下文经 {@link ModelCallTraceHolder} 交给适配器，随请求选项的属性表到达用量监听器。
 *
 * @author cxxl
 */
@Component
@RequiredArgsConstructor
public class ModelInvoker {

    private final ModelAdapter modelAdapter;

    private final OutputSchemaValidator outputSchemaValidator;

    private final ModelErrorClassifier modelErrorClassifier;

    private final ModelCallTraceHolder modelCallTraceHolder;

    /**
     * 对话调用：厂商码由形参给出，模型名在请求体；调用上下文在本方法内创建，调用结束即丢弃。
     *
     * @param vendorCode 厂商码
     * @param request    对话入参（模型/消息/输出约束/超时）
     * @return 出参（原始文本 + 校验后的对象树 + 用量 + 模型标识）
     */
    public GenerationResult chat(String vendorCode, ChatRequest request) {
        return chat(vendorCode, request, new ModelCallTrace());
    }

    /**
     * 对话调用：调用上下文由形参给出，调用结束后从入参读到尝试次数、用量、延迟与错误码。
     *
     * @param vendorCode 厂商码
     * @param request    对话入参（模型/消息/输出约束/超时）
     * @param trace      调用上下文；入参为空即改用本方法内新建的上下文
     * @return 出参（原始文本 + 校验后的对象树 + 用量 + 模型标识）
     */
    public GenerationResult chat(String vendorCode, ChatRequest request, ModelCallTrace trace) {
        ModelCallTrace callTrace = NullUtil.isNull(trace) ? new ModelCallTrace() : trace;
        try {
            checkArguments(vendorCode, request);
            outputSchemaValidator.checkBeforeCall(request.getOutput());
            modelCallTraceHolder.bind(callTrace);
            GenerationResult result = invokeChat(vendorCode, request);
            result.setJson(outputSchemaValidator.validate(request.getOutput(), result.getContent()));
            return result;
        } catch (KnowledgeException e) {
            callTrace.setErrorCode(e.getErrorCode());
            throw e;
        } catch (RuntimeException e) {
            KnowledgeException error = toKnowledgeException(vendorCode, request.getModelKey(), e);
            callTrace.setErrorCode(error.getErrorCode());
            throw error;
        } finally {
            modelCallTraceHolder.clear();
        }
    }

    /**
     * 是否可降级：入参取映射后的知识异常或适配器原始异常，两者结论一致。
     *
     * @param cause 异常
     * @return 可降级为 true
     */
    public boolean canFallback(Throwable cause) {
        return modelErrorClassifier.canFallback(cause);
    }

    /** 一次调用：适配器抛出的异常在本处映射成知识异常，原始异常挂到 cause */
    private GenerationResult invokeChat(String vendorCode, ChatRequest request) {
        try {
            return modelAdapter.chat(vendorCode, request);
        } catch (KnowledgeException e) {
            throw e;
        } catch (RuntimeException e) {
            throw toKnowledgeException(vendorCode, request.getModelKey(), e);
        }
    }

    /** 映射成「错误码 + detail」：detail 带厂商码、模型名与厂商原文消息 */
    private KnowledgeException toKnowledgeException(String vendorCode, String modelKey, RuntimeException cause) {
        ErrorCode errorCode = modelErrorClassifier.classify(cause);
        KnowledgeException error = new KnowledgeException(errorCode,
                "厂商：" + vendorCode + "，模型：" + modelKey + "，原因：" + message(cause));
        error.initCause(cause);
        return error;
    }

    /** 异常原文：消息缺失时取异常类名 */
    private String message(RuntimeException cause) {
        return NullUtil.isNull(cause.getMessage()) ? cause.getClass().getName() : cause.getMessage();
    }

    /** 形参校验：厂商码缺失按未配置的厂商拒绝，入参缺失按参数问题拒绝 */
    private void checkArguments(String vendorCode, ChatRequest request) {
        if (NullUtil.isNull(vendorCode) || vendorCode.isBlank()) {
            throw new KnowledgeException(ErrorCode.MODEL_VENDOR_UNKNOWN, "厂商：" + vendorCode);
        }
        if (NullUtil.isNull(request)) {
            throw new KnowledgeException(ErrorCode.MODEL_BAD_REQUEST, "对话入参为空，厂商：" + vendorCode);
        }
    }
}
