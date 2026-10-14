package com.knowledge.model.dashscope;

import cn.hutool.core.util.StrUtil;
import com.knowledge.common.domain.embed.EmbeddingRequest;
import com.knowledge.common.domain.embed.EmbeddingResult;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import com.knowledge.common.exception.ThrowUtil;
import com.knowledge.common.utils.NullUtil;
import com.knowledge.model.config.ModelVendorProperties;
import com.knowledge.model.engine.ModelInstanceRegistry;
import com.knowledge.model.gateway.ModelGatewayPort;
import com.knowledge.model.listener.ModelCallTrace;
import com.knowledge.model.listener.ModelCallTraceHolder;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.response.EmbeddingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 向量化提供者（OpenAI 兼容协议）：按厂商配置取 LC4j 向量模型实例，把一批文本编码成向量。
 * 厂商从配置里的兼容协议厂商解析（规则 A），模型名取请求体的 {@code EmbeddingRequest.model}，
 * 实例的 baseUrl / apiKey / 超时 / 重试都来自 {@code knowledge.model.vendors.<厂商码>}。
 *
 * <p>出参与入参逐项对应：向量列表与输入一一对应、维度取向量长度、模型回显请求里的模型名、requestId 原样带回。
 * 调用上下文按当前线程取：调用线程已绑定即复用该上下文（多次调用累计在同一份上下文里），
 * 未绑定即新建一份并在本次调用结束时解绑。
 *
 * <p>应答缺向量数据时的失败由底层实现在解引用处抛出（实测为应答缺 {@code data} 字段时的空指针），
 * 本层不另设归类；LC4j 侧的其余异常同样原样透出。
 *
 * @author cxxl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashScopeEmbeddingProvider implements ModelGatewayPort {

    private final ModelVendorProperties vendorProperties;

    private final ModelInstanceRegistry instanceRegistry;

    /** 调用上下文持有：向量调用按它把一次调用的上下文绑在调用线程上，HTTP 客户端装饰器与监听器都按它取回 */
    private final ModelCallTraceHolder modelCallTraceHolder;

    @Override
    public EmbeddingResult embed(EmbeddingRequest request) {
        String vendorCode = compatibleVendorCode();
        ModelVendorProperties.Vendor vendor = vendorProperties.getVendors().get(vendorCode);
        ThrowUtil.throwIf(StrUtil.isBlank(vendor.getApiKey()), () -> new IllegalStateException(
                "向量化 API Key 未配置（knowledge.model.vendors." + vendorCode + ".api-key）"));
        EmbeddingModel embeddingModel = instanceRegistry.embeddingModel(vendorCode, request.getModel());
        ModelCallTrace bound = modelCallTraceHolder.current();
        ModelCallTrace trace = NullUtil.isNull(bound) ? new ModelCallTrace() : bound;
        modelCallTraceHolder.bind(trace);
        try {
            return toEmbeddingResult(embeddingModel.embed(toLc4jRequest(request)), request);
        } finally {
            if (NullUtil.isNull(bound)) {
                modelCallTraceHolder.clear();
            }
        }
    }

    /**
     * 解析本次嵌入使用的厂商（规则 A）：兼容协议厂商只有一个时取该厂商码；
     * 未配置厂商抛「模型厂商未配置」，配置了多个抛同码并提示需要指定厂商。
     *
     * @return 厂商码
     */
    private String compatibleVendorCode() {
        Map<String, ModelVendorProperties.Vendor> vendors = vendorProperties.getVendors();
        Set<String> codes = NullUtil.isNull(vendors) ? Set.of() : vendors.keySet();
        if (codes.isEmpty()) {
            throw new KnowledgeException(ErrorCode.MODEL_VENDOR_UNKNOWN, "厂商：未配置，配置键：knowledge.model.vendors");
        }
        if (codes.size() > 1) {
            throw new KnowledgeException(ErrorCode.MODEL_VENDOR_UNKNOWN,
                    "embed 需要指定厂商，等目录片支持；已配置厂商：" + codes);
        }
        return codes.iterator().next();
    }

    /** 契约 → LC4j 请求：一批文本按请求顺序成为输入，模型名由实例构建时的 modelName 承载 */
    private dev.langchain4j.model.embedding.request.EmbeddingRequest toLc4jRequest(EmbeddingRequest request) {
        dev.langchain4j.model.embedding.request.EmbeddingRequest.Builder builder =
                dev.langchain4j.model.embedding.request.EmbeddingRequest.builder();
        for (String text : NullUtil.isNull(request.getTexts()) ? List.<String>of() : request.getTexts()) {
            builder.input(text);
        }
        return builder.build();
    }

    /** 响应映射：向量列表、维度、模型回显与 requestId 四项口径与入参一致 */
    private EmbeddingResult toEmbeddingResult(EmbeddingResponse response, EmbeddingRequest request) {
        List<dev.langchain4j.data.embedding.Embedding> data = response.embeddings();
        if (NullUtil.isNull(data) || data.isEmpty()) {
            throw new IllegalStateException("向量化响应缺少向量数据");
        }
        List<List<Float>> embeddings = new ArrayList<>(data.size());
        for (dev.langchain4j.data.embedding.Embedding item : data) {
            embeddings.add(item.vectorAsList());
        }
        EmbeddingResult result = new EmbeddingResult();
        result.setEmbeddings(embeddings);
        result.setDimension(embeddings.getFirst().size());
        result.setModel(request.getModel());
        result.setRequestId(request.getRequestId());
        return result;
    }
}
