package com.knowledge.model.dashscope;

import com.knowledge.common.domain.embed.EmbeddingRequest;
import com.knowledge.common.domain.embed.EmbeddingResult;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import com.knowledge.common.exception.ThrowUtil;
import com.knowledge.common.utils.NullUtil;
import com.knowledge.model.config.ModelVendorProperties;
import com.knowledge.model.engine.ModelInstanceRegistry;
import com.knowledge.model.gateway.ModelGatewayPort;
import com.knowledge.model.invoke.ModelInvoker;
import com.knowledge.model.listener.ModelCallTrace;
import com.knowledge.model.listener.ModelCallTraceHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 向量化提供者（OpenAI 兼容协议）：把一批文本编码成向量。
 * 厂商按目录解析「模型 → 厂商」（{@link ModelInstanceRegistry#resolveVendorCode(String)}），
 * 模型名取请求体的 {@code EmbeddingRequest.model}，实例的 baseUrl / apiKey / 超时 / 重试都来自
 * {@code knowledge.model.vendors.<厂商码>}。
 *
 * <p>一次调用经 {@link ModelInvoker} 走适配器：异常按与 chat 同一套错误码映射，
 * 应答缺向量数据归「输出结构校验失败」（40469）。出参与入参逐项对应：向量列表与输入一一对应、
 * 维度取向量长度、模型回显取响应回传值（缺失时才回填请求里的模型名）、requestId 原样带回。
 * 调用上下文按当前线程取：调用线程已绑定即复用该上下文（多次调用累计在同一份上下文里），
 * 未绑定即新建一份并在本次调用结束时解绑。
 *
 * @author cxxl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashScopeEmbeddingProvider implements ModelGatewayPort {

    private final ModelVendorProperties vendorProperties;

    private final ModelInstanceRegistry instanceRegistry;

    private final ModelInvoker modelInvoker;

    /** 调用上下文持有：向量调用按它把一次调用的上下文绑在调用线程上，HTTP 客户端装饰器与监听器都按它取回 */
    private final ModelCallTraceHolder modelCallTraceHolder;

    @Override
    public EmbeddingResult embed(EmbeddingRequest request) {
        String modelKey = NullUtil.isNull(request) ? null : request.getModel();
        String vendorCode = resolveVendorCode(modelKey);
        ModelVendorProperties.Vendor vendor = vendorProperties.getVendors().get(vendorCode);
        ThrowUtil.throwIf(NullUtil.isNull(vendor) || NullUtil.isNull(vendor.getApiKey()) || vendor.getApiKey().isBlank(),
                () -> new IllegalStateException(
                        "向量化 API Key 未配置（knowledge.model.vendors." + vendorCode + ".api-key）"));
        ModelCallTrace bound = modelCallTraceHolder.current();
        ModelCallTrace trace = NullUtil.isNull(bound) ? new ModelCallTrace() : bound;
        modelCallTraceHolder.bind(trace);
        try {
            return modelInvoker.embed(vendorCode, request, trace);
        } finally {
            if (NullUtil.isNull(bound)) {
                modelCallTraceHolder.clear();
            }
        }
    }

    /**
     * 解析厂商：配置里一个厂商都没有时按「模型厂商未配置」直接拒绝，其余交给目录解析
     * （目录优先，目录未收录时回落到唯一厂商）。
     */
    private String resolveVendorCode(String modelKey) {
        Map<String, ModelVendorProperties.Vendor> vendors = vendorProperties.getVendors();
        if (NullUtil.isNull(vendors) || vendors.isEmpty()) {
            throw new KnowledgeException(ErrorCode.MODEL_VENDOR_UNKNOWN, "厂商：未配置，配置键：knowledge.model.vendors");
        }
        return instanceRegistry.resolveVendorCode(modelKey);
    }
}
