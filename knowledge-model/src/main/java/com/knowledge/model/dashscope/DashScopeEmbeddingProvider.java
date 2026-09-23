package com.knowledge.model.dashscope;

import cn.hutool.core.util.StrUtil;
import com.knowledge.common.domain.embed.EmbeddingRequest;
import com.knowledge.common.domain.embed.EmbeddingResult;
import com.knowledge.model.gateway.ModelGatewayPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 阿里云 DashScope 向量化提供者（OpenAI 兼容协议）：POST {baseUrl}/embeddings，Bearer 鉴权。
 * 失败/空响应 → 抛 IllegalStateException（管线侧按批次重试）。
 *
 * @author cxxl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DashScopeEmbeddingProvider implements ModelGatewayPort {

    private final DashScopeProperties properties;

    /** 测试可注入；运行时惰性构建 */
    private RestClient client;

    @Override
    public EmbeddingResult embed(EmbeddingRequest request) {
        if (StrUtil.isBlank(properties.getApiKey())) {
            throw new IllegalStateException("DashScope API Key 未配置（knowledge.model.dashscope.api-key）");
        }
        Map<String, Object> body = Map.of(
                "model", request.getModel(),
                "input", request.getTexts());
        try {
            Map<String, Object> response = restClient().post()
                    .uri("/embeddings")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return mapResponse(response, request);
        } catch (RestClientException e) {
            throw new IllegalStateException("DashScope 向量化失败: " + e.getMessage(), e);
        }
    }

    private RestClient restClient() {
        if (client != null) {
            return client;
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        client = RestClient.builder().baseUrl(properties.getBaseUrl()).requestFactory(factory).build();
        return client;
    }

    /** 响应映射：data[].embedding 与输入一一对应；无数据/条目非法抛错 */
    private EmbeddingResult mapResponse(Map<String, Object> response, EmbeddingRequest request) {
        Object dataObj = response == null ? null : response.get("data");
        if (!(dataObj instanceof List<?> data) || data.isEmpty()) {
            throw new IllegalStateException("DashScope 响应缺少向量数据");
        }
        List<List<Float>> embeddings = new ArrayList<>();
        Integer dimension = null;
        for (Object item : data) {
            if (!(item instanceof Map<?, ?> entry)) {
                throw new IllegalStateException("DashScope 响应条目格式非法");
            }
            Object embedding = entry.get("embedding");
            if (!(embedding instanceof List<?> vector)) {
                throw new IllegalStateException("DashScope 响应条目缺少 embedding");
            }
            if (dimension == null) {
                dimension = vector.size();
            }
            List<Float> floats = new ArrayList<>(vector.size());
            for (Object value : vector) {
                floats.add(Float.parseFloat(String.valueOf(value)));
            }
            embeddings.add(floats);
        }
        EmbeddingResult result = new EmbeddingResult();
        result.setEmbeddings(embeddings);
        result.setDimension(dimension);
        result.setModel(request.getModel());
        result.setRequestId(request.getRequestId());
        return result;
    }
}
