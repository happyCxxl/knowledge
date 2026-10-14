package com.knowledge.model.invoke;

import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.knowledge.common.domain.model.ChatMessage;
import com.knowledge.common.domain.model.ChatRequest;
import com.knowledge.common.domain.model.GenerationResult;
import com.knowledge.common.domain.model.MessageRole;
import com.knowledge.common.domain.model.OutputFormat;
import com.knowledge.common.domain.model.OutputSpec;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import com.knowledge.model.adapter.OpenAiCompatibleAdapter;
import com.knowledge.model.config.ModelVendorProperties;
import com.knowledge.model.engine.ModelInstanceRegistry;
import com.knowledge.model.engine.OpenAiCompatibleEngineFactory;
import com.knowledge.model.listener.ModelCallTraceHolder;
import com.knowledge.model.listener.UsageRecordingListener;
import com.knowledge.model.support.FakeOpenAiServer;
import com.knowledge.model.validate.OutputSchemaValidator;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.ContentFilteredException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.exception.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 调用器单测（假 OpenAI 兼容端点）：一次调用的请求次数、异常到错误码的映射与可降级结论、
 * 结构校验失败的口径与失败前的调用前拒绝。
 *
 * @author cxxl
 */
class ModelInvokerTest {

    private static final String VENDOR = "dashscope";

    private static final String MODEL = "qwen-max";

    /** 严格校验用 schema：name 必填且为字符串，age 为整数 */
    private static final String SCHEMA = "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},"
            + "\"age\":{\"type\":\"integer\"}},\"required\":[\"name\"],\"additionalProperties\":false}";

    private FakeOpenAiServer server;

    private ModelInvoker invoker;

    @BeforeEach
    void setUp() throws IOException {
        server = new FakeOpenAiServer();
        server.respondWith(200, chatResponse("pong"));
        invoker = invoker(server.baseUrl(), 10000);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void chatShouldCallOnceAndReturnContentUsageAndModel() {
        GenerationResult result = invoker.chat(VENDOR, request(OutputFormat.TEXT, null));

        assertEquals("pong", result.getContent());
        assertEquals(3, result.getUsage().getPromptTokens());
        assertEquals(2, result.getUsage().getCompletionTokens());
        assertEquals(5, result.getUsage().getTotalTokens());
        assertEquals(MODEL, result.getModel());
        assertNull(result.getModelVersion());
        assertNull(result.getJson());
        assertEquals(1, server.requestCount());
    }

    @Test
    void textOutputShouldPassWithoutValidation() {
        server.respondWith(200, chatResponse("这不是 JSON，也不做结构校验"));

        GenerationResult result = invoker.chat(VENDOR, request(OutputFormat.TEXT, null));

        assertEquals("这不是 JSON，也不做结构校验", result.getContent());
        assertNull(result.getJson());
    }

    @Test
    void jsonObjectOutputShouldFillParsedTree() {
        server.respondWith(200, chatResponse("{\"name\":\"张三\",\"age\":3}"));

        GenerationResult result = invoker.chat(VENDOR, request(OutputFormat.JSON_OBJECT, null));

        Map<?, ?> tree = assertInstanceOf(Map.class, result.getJson());
        assertEquals("张三", tree.get("name"));
        assertEquals(3, tree.get("age"));
        assertEquals("{\"name\":\"张三\",\"age\":3}", result.getContent());
    }

    @Test
    void jsonSchemaOutputSatisfyingSchemaShouldFillParsedTree() {
        server.respondWith(200, chatResponse("{\"name\":\"张三\",\"age\":3}"));

        GenerationResult result = invoker.chat(VENDOR, request(OutputFormat.JSON_SCHEMA, SCHEMA));

        Map<?, ?> tree = assertInstanceOf(Map.class, result.getJson());
        assertEquals("张三", tree.get("name"));
        assertEquals(3, tree.get("age"));
        assertEquals(1, server.requestCount());
    }

    @Test
    void jsonObjectOutputNotJsonShouldFailValidation() {
        server.respondWith(200, chatResponse("not-json"));

        KnowledgeException error = chatError(request(OutputFormat.JSON_OBJECT, null));

        assertEquals(ErrorCode.MODEL_OUTPUT_INVALID, error.getErrorCode());
        assertEquals(40469, error.getCode());
        assertTrue(invoker.canFallback(error));
        assertEquals(1, server.requestCount());
    }

    @Test
    void jsonSchemaOutputViolatingSchemaShouldFailValidation() {
        server.respondWith(200, chatResponse("{\"age\":\"三\"}"));

        KnowledgeException error = chatError(request(OutputFormat.JSON_SCHEMA, SCHEMA));

        assertEquals(ErrorCode.MODEL_OUTPUT_INVALID, error.getErrorCode());
        assertTrue(error.getMessage().contains("schema"), error.getMessage());
        assertTrue(invoker.canFallback(error));
    }

    @Test
    void jsonSchemaWithoutSchemaShouldBeRejectedBeforeRequest() {
        KnowledgeException error = chatError(request(OutputFormat.JSON_SCHEMA, "  "));

        assertEquals(ErrorCode.MODEL_BAD_REQUEST, error.getErrorCode());
        assertTrue(error.getMessage().contains("schema"), error.getMessage());
        assertEquals(0, server.requestCount());
    }

    @Test
    void blankVendorCodeShouldBeRejectedBeforeRequest() {
        KnowledgeException error = assertThrows(KnowledgeException.class,
                () -> invoker.chat("  ", request(OutputFormat.TEXT, null)));

        assertEquals(ErrorCode.MODEL_VENDOR_UNKNOWN, error.getErrorCode());
        assertEquals(0, server.requestCount());
    }

    @Test
    void nullRequestShouldBeRejectedBeforeRequest() {
        KnowledgeException error = assertThrows(KnowledgeException.class, () -> invoker.chat(VENDOR, null));

        assertEquals(ErrorCode.MODEL_BAD_REQUEST, error.getErrorCode());
        assertEquals(0, server.requestCount());
    }

    @Test
    void unknownVendorShouldBeRejectedByRegistry() {
        KnowledgeException error = assertThrows(KnowledgeException.class,
                () -> invoker.chat("nope", request(OutputFormat.TEXT, null)));

        assertEquals(ErrorCode.MODEL_VENDOR_UNKNOWN, error.getErrorCode());
        assertTrue(error.getMessage().contains("nope"), error.getMessage());
        assertEquals(0, server.requestCount());
    }

    @Test
    void blankModelKeyShouldBeRejectedByRegistry() {
        ChatRequest request = request(OutputFormat.TEXT, null);
        request.setModelKey("  ");

        KnowledgeException error = chatError(request);

        assertEquals(ErrorCode.MODEL_BAD_REQUEST, error.getErrorCode());
        assertEquals(0, server.requestCount());
    }

    @Test
    void rateLimitedShouldMapToRateLimitedAndAllowFallback() {
        server.respondWith(429, errorBody("Throttling", "Requests rate limit exceeded"));

        KnowledgeException error = chatError(request(OutputFormat.TEXT, null));

        assertEquals(ErrorCode.MODEL_RATE_LIMITED, error.getErrorCode());
        assertInstanceOf(RateLimitException.class, error.getCause());
        assertTrue(invoker.canFallback(error));
        assertTrue(invoker.canFallback(error.getCause()));
        assertEquals(1, server.requestCount());
    }

    @Test
    void authFailedShouldMapToAuthFailedAndBlockFallback() {
        server.respondWith(401, errorBody("InvalidApiKey", "Invalid API-key provided"));

        KnowledgeException error = chatError(request(OutputFormat.TEXT, null));

        assertEquals(ErrorCode.MODEL_AUTH_FAILED, error.getErrorCode());
        assertInstanceOf(AuthenticationException.class, error.getCause());
        assertFalse(invoker.canFallback(error));
        assertEquals(1, server.requestCount());
    }

    @Test
    void badRequestShouldMapToBadRequestAndBlockFallback() {
        server.respondWith(400, errorBody("InvalidParameter", "Range of input length should be [1, 6000]"));

        KnowledgeException error = chatError(request(OutputFormat.TEXT, null));

        assertEquals(ErrorCode.MODEL_BAD_REQUEST, error.getErrorCode());
        assertInstanceOf(InvalidRequestException.class, error.getCause());
        assertFalse(invoker.canFallback(error));
    }

    @Test
    void modelNotFoundShouldMapToNotFoundAndBlockFallback() {
        server.respondWith(404, errorBody("ModelNotFound", "The model does not exist"));

        KnowledgeException error = chatError(request(OutputFormat.TEXT, null));

        assertEquals(ErrorCode.MODEL_NOT_FOUND, error.getErrorCode());
        assertInstanceOf(ModelNotFoundException.class, error.getCause());
        assertFalse(invoker.canFallback(error));
    }

    @Test
    void contentFilteredShouldMapToBadRequestAndBlockFallback() {
        server.respondWith(200, refusalResponse("DataInspectionFailed"));

        KnowledgeException error = chatError(request(OutputFormat.TEXT, null));

        assertEquals(ErrorCode.MODEL_BAD_REQUEST, error.getErrorCode(), String.valueOf(error.getCause()));
        assertInstanceOf(ContentFilteredException.class, error.getCause());
        assertFalse(invoker.canFallback(error));
    }

    @Test
    void serverErrorShouldMapToServerErrorAndAllowFallback() {
        server.respondWith(500, errorBody("InternalError", "Internal server error"));

        KnowledgeException error = chatError(request(OutputFormat.TEXT, null));

        assertEquals(ErrorCode.MODEL_SERVER_ERROR, error.getErrorCode());
        assertInstanceOf(InternalServerException.class, error.getCause());
        assertTrue(invoker.canFallback(error));
    }

    @Test
    void responseWithoutChoicesShouldMapToServerErrorAndAllowFallback() {
        server.respondWith(200, "{\"id\":\"c9\",\"model\":\"" + MODEL + "\"}");

        KnowledgeException error = chatError(request(OutputFormat.TEXT, null));

        assertEquals(ErrorCode.MODEL_SERVER_ERROR, error.getErrorCode());
        assertInstanceOf(InternalServerException.class, error.getCause());
        assertTrue(invoker.canFallback(error));
        assertEquals(1, server.requestCount());
    }

    @Test
    void timeoutShouldMapToTimeoutAndAllowFallback() {
        invoker = invoker(server.baseUrl(), 300);
        server.delayMillis(1500);

        KnowledgeException error = chatError(request(OutputFormat.TEXT, null));

        assertEquals(ErrorCode.MODEL_TIMEOUT, error.getErrorCode());
        assertInstanceOf(TimeoutException.class, error.getCause());
        assertTrue(invoker.canFallback(error));
    }

    @Test
    void unparsableResponseBodyShouldMapToOutputInvalidAndAllowFallback() {
        server.respondWith(200, "not-json");

        KnowledgeException error = chatError(request(OutputFormat.TEXT, null));

        assertEquals(ErrorCode.MODEL_OUTPUT_INVALID, error.getErrorCode());
        assertTrue(hasCause(error, JsonProcessingException.class), String.valueOf(error.getCause()));
        assertTrue(invoker.canFallback(error));
        assertEquals(1, server.requestCount());
    }

    @Test
    void connectionRefusedShouldMapToUnreachableAndAllowFallback() throws IOException {
        invoker = invoker("http://127.0.0.1:" + closedPort() + "/v1", 5000);

        KnowledgeException error = chatError(request(OutputFormat.TEXT, null));

        assertEquals(ErrorCode.MODEL_UNREACHABLE, error.getErrorCode());
        assertTrue(hasCause(error, ConnectException.class), String.valueOf(error.getCause()));
        assertTrue(invoker.canFallback(error));
        assertEquals(0, server.requestCount());
    }

    @Test
    void mappedDetailShouldCarryVendorModelAndVendorMessage() {
        server.respondWith(429, errorBody("Throttling", "Requests rate limit exceeded"));

        KnowledgeException error = chatError(request(OutputFormat.TEXT, null));

        assertTrue(error.getMessage().contains(VENDOR), error.getMessage());
        assertTrue(error.getMessage().contains(MODEL), error.getMessage());
        assertTrue(error.getMessage().contains("Requests rate limit exceeded"), error.getMessage());
    }

    /** 调用并断言抛出知识异常 */
    private KnowledgeException chatError(ChatRequest request) {
        return assertThrows(KnowledgeException.class, () -> invoker.chat(VENDOR, request));
    }

    /** 带假服务的调用器：重试为 0（失败即抛，请求次数与调用次数一致） */
    private ModelInvoker invoker(String baseUrl, int timeoutMs) {
        ModelVendorProperties properties = new ModelVendorProperties();
        Map<String, ModelVendorProperties.Vendor> vendors = new LinkedHashMap<>();
        ModelVendorProperties.Vendor vendor = new ModelVendorProperties.Vendor();
        vendor.setBaseUrl(baseUrl);
        vendor.setApiKey("sk-invoker");
        vendor.setTimeoutMs(timeoutMs);
        vendor.setMaxRetries(0);
        vendors.put(VENDOR, vendor);
        properties.setVendors(vendors);
        ModelCallTraceHolder traceHolder = new ModelCallTraceHolder();
        OpenAiCompatibleEngineFactory engineFactory =
                new OpenAiCompatibleEngineFactory(new UsageRecordingListener(traceHolder), traceHolder);
        ModelInstanceRegistry registry = new ModelInstanceRegistry(properties, engineFactory);
        return new ModelInvoker(new OpenAiCompatibleAdapter(registry, traceHolder), new OutputSchemaValidator(),
                new ModelErrorClassifier(), traceHolder);
    }

    /** 对话入参：单条用户消息 + 指定输出约束 */
    private ChatRequest request(OutputFormat format, String schema) {
        ChatRequest request = new ChatRequest();
        request.setModelKey(MODEL);
        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage message = new ChatMessage();
        message.setRole(MessageRole.USER);
        message.setContent("ping");
        messages.add(message);
        request.setMessages(messages);
        OutputSpec output = new OutputSpec();
        output.setFormat(format);
        output.setSchema(schema);
        request.setOutput(output);
        request.setTimeoutMs(10000);
        return request;
    }

    /** 正常应答：单条助手消息 + 用量 */
    private String chatResponse(String content) {
        return "{\"id\":\"c1\",\"model\":\"" + MODEL + "\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\","
                + "\"content\":" + JSONUtil.quote(content) + "},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2,\"total_tokens\":5}}";
    }

    /** 内容审核应答：助手消息只带 refusal 字段 */
    private String refusalResponse(String refusal) {
        return "{\"id\":\"c2\",\"model\":\"" + MODEL + "\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\","
                + "\"content\":null,\"refusal\":" + JSONUtil.quote(refusal) + "},\"finish_reason\":\"stop\"}]}";
    }

    /** 错误应答体（OpenAI 兼容形态） */
    private String errorBody(String code, String message) {
        return "{\"error\":{\"code\":" + JSONUtil.quote(code) + ",\"message\":" + JSONUtil.quote(message)
                + ",\"type\":\"invalid_request_error\"}}";
    }

    /** 取一个刚释放的本机端口，连接该端口即被拒 */
    private int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    /** 异常链上是否出现该类型（含自身） */
    private boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable node = error;
        while (node != null) {
            if (type.isInstance(node)) {
                return true;
            }
            node = node.getCause() == node ? null : node.getCause();
        }
        return false;
    }
}
