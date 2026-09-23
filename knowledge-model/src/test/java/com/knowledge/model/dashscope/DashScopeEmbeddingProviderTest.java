package com.knowledge.model.dashscope;

import com.knowledge.common.domain.embed.EmbeddingRequest;
import com.knowledge.common.domain.embed.EmbeddingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

/**
 * DashScope 向量化提供者单测：请求组装（模型/文本/Bearer 鉴权）+ 响应映射 + 异常路径。
 *
 * @author cxxl
 */
class DashScopeEmbeddingProviderTest {

    private DashScopeProperties properties;

    private DashScopeEmbeddingProvider provider;

    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        properties = new DashScopeProperties();
        properties.setBaseUrl("http://localhost:9999");
        properties.setApiKey("test-key");
        provider = new DashScopeEmbeddingProvider(properties);
        RestClient.Builder builder = RestClient.builder().baseUrl(properties.getBaseUrl());
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        // 注入测试 RestClient：provider 每次调用自建 client，此处通过反射替换为绑定 mock 的实例
        org.springframework.test.util.ReflectionTestUtils.setField(provider, "client", client);
    }

    @Test
    void embedShouldSendTextsAndMapVectors() {
        server.expect(requestTo("http://localhost:9999/embeddings"))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(request -> {
                    String body = ((org.springframework.mock.http.client.MockClientHttpRequest) request).getBodyAsString();
                    assertEquals(true, body.contains("text-embedding-v4"), body);
                    assertEquals(true, body.contains("第一句"), body);
                })
                .andRespond(withSuccess(
                        "{\"data\":[{\"embedding\":[0.1,0.2]},{\"embedding\":[0.3,0.4]}]}",
                        MediaType.APPLICATION_JSON));

        EmbeddingRequest request = new EmbeddingRequest();
        request.setModel("text-embedding-v4");
        request.setTexts(List.of("第一句", "第二句"));
        request.setRequestId("req-1");

        EmbeddingResult result = provider.embed(request);

        assertNotNull(result);
        assertEquals(2, result.getEmbeddings().size());
        assertEquals(2, result.getDimension());
        assertEquals(0.2f, result.getEmbeddings().getFirst().get(1));
        assertEquals("req-1", result.getRequestId());
    }

    @Test
    void embedWithServerErrorShouldThrow() {
        server.expect(requestTo("http://localhost:9999/embeddings"))
                .andRespond(withServerError());

        EmbeddingRequest request = new EmbeddingRequest();
        request.setModel("text-embedding-v4");
        request.setTexts(List.of("x"));

        assertThrows(IllegalStateException.class, () -> provider.embed(request));
    }

    @Test
    void embedWithoutApiKeyShouldThrow() {
        properties.setApiKey(null);
        EmbeddingRequest request = new EmbeddingRequest();
        request.setModel("text-embedding-v4");
        request.setTexts(List.of("x"));
        assertThrows(IllegalStateException.class, () -> provider.embed(request));
    }
}
