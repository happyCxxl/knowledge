package com.knowledge.model.invoke;

import com.knowledge.common.domain.embed.EmbeddingRequest;
import com.knowledge.common.domain.embed.EmbeddingResult;
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

import java.util.List;
import java.util.function.Supplier;

/**
 * 模型调用器：取适配器执行一次调用，对结果做结构校验后返回。
 * 同一实例内的重试与退避由底层实例承担，本类不另加重试；调用失败的异常在本类映射成错误码，
 * 「是否可降级」由 {@link ModelErrorClassifier} 当场判定并经 {@link #canFallback(Throwable)} 暴露。
 * 调用上下文经 {@link ModelCallTraceHolder} 交给适配器，随请求选项的属性表到达用量监听器。
 * 对话与向量两个模态共用同一套上下文绑定与异常映射，错误码口径一致。
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
        String modelKey = NullUtil.isNull(request) ? null : request.getModelKey();
        return invoke(vendorCode, modelKey, trace, () -> {
            outputSchemaValidator.checkBeforeCall(request.getOutput());
            GenerationResult generation = modelAdapter.chat(vendorCode, request);
            generation.setJson(outputSchemaValidator.validate(request.getOutput(), generation.getContent()));
            return generation;
        });
    }

    /**
     * 向量编码调用：厂商码由形参给出，模型名在请求体；错误码映射与可降级判定与对话调用同一套。
     * 向量数据缺失按「输出结构校验失败」（40469）拒绝，避免该失败退化成服务端错误。
     *
     * @param vendorCode 厂商码
     * @param request    向量入参（模型/文本/超时/批大小/requestId）
     * @param trace      调用上下文；入参为空即改用本方法内新建的上下文
     * @return 向量列表 + 维度 + 模型回显 + requestId
     */
    public EmbeddingResult embed(String vendorCode, EmbeddingRequest request, ModelCallTrace trace) {
        String modelKey = NullUtil.isNull(request) ? null : request.getModel();
        return invoke(vendorCode, modelKey, trace, () -> {
            EmbeddingResult result = modelAdapter.embed(vendorCode, request);
            List<List<Float>> embeddings = NullUtil.isNull(result) ? null : result.getEmbeddings();
            if (NullUtil.isNull(embeddings) || embeddings.isEmpty()) {
                throw new KnowledgeException(ErrorCode.MODEL_OUTPUT_INVALID,
                        "厂商：" + vendorCode + "，模型：" + modelKey + "，原因：向量化响应缺少向量数据");
            }
            return result;
        });
    }

    /**
     * 一次调用的公共外壳：形参校验 → 绑定调用上下文 → 执行本次调用 → 失败映射错误码并回写上下文 → 解绑。
     * 对话与向量共用本方法，两个模态的失败口径保持一致。
     *
     * @param vendorCode 厂商码
     * @param modelKey   模型名（对话取 ChatRequest.modelKey，向量取 EmbeddingRequest.model）
     * @param trace      调用上下文；入参为空即改用本方法内新建的上下文
     * @param call       本次调用（适配器调用与出参校验）
     * @return 本次调用的出参
     */
    private <T> T invoke(String vendorCode, String modelKey, ModelCallTrace trace, Supplier<T> call) {
        ModelCallTrace callTrace = NullUtil.isNull(trace) ? new ModelCallTrace() : trace;
        try {
            checkArguments(vendorCode, modelKey);
            modelCallTraceHolder.bind(callTrace);
            return call.get();
        } catch (KnowledgeException e) {
            callTrace.setErrorCode(e.getErrorCode());
            throw e;
        } catch (RuntimeException e) {
            KnowledgeException error = toKnowledgeException(vendorCode, modelKey, e);
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

    /** 形参校验：厂商码缺失按未配置的厂商拒绝，模型名缺失按参数问题拒绝 */
    private void checkArguments(String vendorCode, String modelKey) {
        if (NullUtil.isNull(vendorCode) || vendorCode.isBlank()) {
            throw new KnowledgeException(ErrorCode.MODEL_VENDOR_UNKNOWN, "厂商：" + vendorCode);
        }
        if (NullUtil.isNull(modelKey)) {
            throw new KnowledgeException(ErrorCode.MODEL_BAD_REQUEST, "模型名不能为空，厂商：" + vendorCode);
        }
    }
}
