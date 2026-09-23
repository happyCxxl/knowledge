package com.knowledge.model.gateway;

import com.knowledge.common.domain.embed.EmbeddingRequest;
import com.knowledge.common.domain.embed.EmbeddingResult;

/**
 * 模型能力网关端口：向量编码能力入口，供应商可替换（阿里云 DashScope 先行接入）。
 * 失败抛异常（调用方按批次重试）。
 *
 * @author cxxl
 */
public interface ModelGatewayPort {

    /**
     * 批量向量编码：texts 原样编码（顺序即结果顺序）。
     *
     * @param request 本批输入（模型/文本/超时/批大小/requestId）
     * @return 向量列表（与输入一一对应）+ 维度 + 模型回显
     */
    EmbeddingResult embed(EmbeddingRequest request);
}
