package com.knowledge.model.dashscope;

import com.knowledge.common.domain.embed.EmbeddingRequest;
import com.knowledge.common.domain.embed.EmbeddingResult;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import com.knowledge.model.config.ModelVendorProperties;
import com.knowledge.model.engine.ModelInstanceRegistry;
import com.knowledge.model.engine.OpenAiCompatibleEngineFactory;
import com.knowledge.model.listener.ModelCallTrace;
import com.knowledge.model.listener.ModelCallTraceHolder;
import com.knowledge.model.listener.UsageRecordingListener;
import com.knowledge.model.support.FakeOpenAiServer;
import dev.langchain4j.exception.InternalServerException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 向量化提供者单测（本地假 OpenAI 兼容端点）：请求路径、Bearer 头、模型名与文本顺序，
 * 响应映射的向量条数与顺序、维度、模型回显、requestId，缺向量数据与密钥缺失的拒绝口径，
 * 以及厂商解析规则 A 与调用上下文观测三项。
 *
 * @author cxxl
 */
class DashScopeEmbeddingProviderTest {

    private static final String VENDOR = "dashscope";

    private static final String MODEL = "text-embedding-v4";

    private static final String EMBEDDING_RESPONSE = "{\"object\":\"list\",\"model\":\"text-embedding-v4\","
            + "\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":[0.1,0.2]},"
            + "{\"object\":\"embedding\",\"index\":1,\"embedding\":[0.3,0.4]}],"
            + "\"usage\":{\"prompt_tokens\":3,\"total_tokens\":3}}";

    private FakeOpenAiServer server;

    private ModelVendorProperties vendorProperties;

    private ModelCallTraceHolder traceHolder;

    private DashScopeEmbeddingProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        server = new FakeOpenAiServer();
        server.respondWith(200, EMBEDDING_RESPONSE);
        traceHolder = new ModelCallTraceHolder();
        vendorProperties = properties(Map.of(VENDOR, vendor("sk-test", 0)));
        provider = provider(vendorProperties);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void embedShouldSendPathModelAndBearerHeader() {
        EmbeddingRequest request = request();

        provider.embed(request);

        assertEquals(FakeOpenAiServer.EMBEDDING_PATH, server.lastPath());
        assertEquals("Bearer sk-test", server.lastAuthorization());
        assertTrue(server.lastBody().contains("\"" + MODEL + "\""), server.lastBody());
        assertTrue(server.lastBody().contains("第一句"), server.lastBody());
        assertTrue(server.lastBody().contains("第二句"), server.lastBody());
        assertEquals(1, server.requestCount());
    }

    @Test
    void embedShouldMapVectorsDimensionModelAndRequestId() {
        EmbeddingResult result = provider.embed(request());

        assertNotNull(result);
        assertEquals(2, result.getEmbeddings().size());
        assertEquals(2, result.getDimension());
        assertEquals(0.1f, result.getEmbeddings().get(0).get(0));
        assertEquals(0.2f, result.getEmbeddings().get(0).get(1));
        assertEquals(0.3f, result.getEmbeddings().get(1).get(0));
        assertEquals(0.4f, result.getEmbeddings().get(1).get(1));
        assertEquals(MODEL, result.getModel());
        assertEquals("req-1", result.getRequestId());
    }

    @Test
    void singleVendorShouldBeUsedWithoutVendorParameter() {
        vendorProperties.setVendors(Map.of("aliyun", vendor("sk-aliyun", 0)));

        EmbeddingResult result = provider(vendorProperties).embed(request());

        assertEquals(2, result.getEmbeddings().size());
        assertEquals("Bearer sk-aliyun", server.lastAuthorization());
    }

    @Test
    void multipleVendorsShouldBeRejectedWithPrompt() {
        vendorProperties.setVendors(vendors(vendor("sk-a", 0), "second", vendor("sk-b", 0)));

        KnowledgeException error = assertThrows(KnowledgeException.class,
                () -> provider(vendorProperties).embed(request()));

        assertEquals(ErrorCode.MODEL_VENDOR_UNKNOWN, error.getErrorCode());
        assertTrue(error.getMessage().contains("embed 需要指定厂商，等目录片支持"), error.getMessage());
        assertTrue(error.getMessage().contains("second"), error.getMessage());
        assertEquals(0, server.requestCount());
    }

    @Test
    void emptyVendorsShouldBeRejectedWithVendorUnknown() {
        vendorProperties.setVendors(Map.of());

        KnowledgeException error = assertThrows(KnowledgeException.class,
                () -> provider(vendorProperties).embed(request()));

        assertEquals(ErrorCode.MODEL_VENDOR_UNKNOWN, error.getErrorCode());
        assertEquals(0, server.requestCount());
    }

    @Test
    void responseWithoutDataShouldThrow() {
        server.respondWith(200, "{\"object\":\"list\",\"model\":\"text-embedding-v4\"}");

        assertThrows(NullPointerException.class, () -> provider.embed(request()));
    }

    @Test
    void malformedResponseShouldThrow() {
        server.respondWith(200, "not-json");

        RuntimeException error = assertThrows(RuntimeException.class, () -> provider.embed(request()));

        assertNotNull(error);
    }

    @Test
    void serverErrorShouldThrow() {
        server.respondWith(500, "{\"error\":{\"message\":\"boom\"}}");

        InternalServerException error = assertThrows(InternalServerException.class, () -> provider.embed(request()));

        assertNotNull(error);
        assertEquals(1, server.requestCount());
    }

    @Test
    void embedWithoutApiKeyShouldThrow() {
        vendorProperties.setVendors(Map.of(VENDOR, vendor("  ", 0)));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> provider(vendorProperties).embed(request()));

        assertTrue(error.getMessage().contains("knowledge.model.vendors." + VENDOR + ".api-key"), error.getMessage());
        assertEquals(0, server.requestCount());
    }

    @Test
    void boundTraceShouldRecordAttemptLatencyAndUsage() {
        ModelCallTrace trace = new ModelCallTrace();

        traceHolder.bind(trace);
        try {
            provider.embed(request());
        } finally {
            traceHolder.clear();
        }

        assertEquals(1, server.requestCount());
        assertEquals(1, trace.attempts());
        assertEquals(0, trace.retryCount());
        assertNotNull(trace.lastAttemptLatencyMs());
        assertNotNull(trace.latencyMs());
        assertNotNull(trace.usage());
        assertEquals(3, trace.usage().getPromptTokens());
        assertNull(trace.usage().getCompletionTokens());
        assertEquals(3, trace.usage().getTotalTokens());
        assertNull(trace.errorCode());
        assertNull(trace.error());
    }

    @Test
    void responseWithoutUsageShouldLeaveUsageUnknown() {
        server.respondWith(200, "{\"object\":\"list\",\"model\":\"text-embedding-v4\","
                + "\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":[0.1,0.2]}]}");
        ModelCallTrace trace = new ModelCallTrace();

        traceHolder.bind(trace);
        try {
            provider.embed(request());
        } finally {
            traceHolder.clear();
        }

        assertEquals(1, trace.attempts());
        assertNull(trace.usage());
        assertNotNull(trace.latencyMs());
    }

    @Test
    void failedCallShouldRecordErrorAndLatencyOnTrace() {
        server.respondWith(500, "{\"error\":{\"message\":\"boom\"}}");
        ModelCallTrace trace = new ModelCallTrace();

        traceHolder.bind(trace);
        try {
            assertThrows(InternalServerException.class, () -> provider.embed(request()));
        } finally {
            traceHolder.clear();
        }

        assertEquals(1, trace.attempts());
        assertNotNull(trace.error());
        assertNotNull(trace.latencyMs());
        assertEquals(1, server.requestCount());
    }

    @Test
    void embedWithoutBoundTraceShouldStillWork() {
        EmbeddingResult result = provider.embed(request());

        assertEquals(2, result.getEmbeddings().size());
        assertEquals(1, server.requestCount());
    }

    /** 带假服务端点的提供者：厂商配置、实例注册表与调用上下文持有器三者按用例装配 */
    private DashScopeEmbeddingProvider provider(ModelVendorProperties properties) {
        OpenAiCompatibleEngineFactory engineFactory =
                new OpenAiCompatibleEngineFactory(new UsageRecordingListener(traceHolder), traceHolder);
        return new DashScopeEmbeddingProvider(properties, new ModelInstanceRegistry(properties, engineFactory),
                traceHolder);
    }

    /** 指向假服务的厂商配置：密钥与重试按用例给值，超时取 10000 毫秒 */
    private ModelVendorProperties.Vendor vendor(String apiKey, Integer maxRetries) {
        ModelVendorProperties.Vendor vendor = new ModelVendorProperties.Vendor();
        vendor.setBaseUrl(server.baseUrl());
        vendor.setApiKey(apiKey);
        vendor.setTimeoutMs(10000);
        vendor.setMaxRetries(maxRetries);
        return vendor;
    }

    /** 厂商配置容器：键序按入参顺序固定 */
    private ModelVendorProperties properties(Map<String, ModelVendorProperties.Vendor> vendors) {
        ModelVendorProperties properties = new ModelVendorProperties();
        properties.setVendors(vendors);
        return properties;
    }

    /** 两个厂商的配置容器：first 在前，otherCode 在后 */
    private Map<String, ModelVendorProperties.Vendor> vendors(ModelVendorProperties.Vendor first, String otherCode,
                                                             ModelVendorProperties.Vendor other) {
        Map<String, ModelVendorProperties.Vendor> vendors = new LinkedHashMap<>();
        vendors.put(VENDOR, first);
        vendors.put(otherCode, other);
        return vendors;
    }

    /** 向量入参：两条文本 + 模型名 + requestId */
    private EmbeddingRequest request() {
        EmbeddingRequest request = new EmbeddingRequest();
        request.setModel(MODEL);
        request.setTexts(List.of("第一句", "第二句"));
        request.setRequestId("req-1");
        return request;
    }
}
