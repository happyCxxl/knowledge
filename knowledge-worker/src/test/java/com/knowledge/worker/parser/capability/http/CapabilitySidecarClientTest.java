package com.knowledge.worker.parser.capability.http;

import com.knowledge.common.domain.parse.BBox;
import com.knowledge.common.domain.parse.capability.TableRegion;
import com.knowledge.common.domain.parse.capability.TableResult;
import com.knowledge.worker.parser.capability.CapabilityProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 能力 sidecar 契约单测（假 sidecar = JDK HttpServer）：请求体按契约发出去、响应按契约收回来；
 * 未配置地址 / 非 2xx / 超时都返回 null（据此走内置降级）。
 *
 * @author cxxl
 */
class CapabilitySidecarClientTest {

    private HttpServer server;
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private volatile String responseBody = "{}";
    private volatile int statusCode = 200;
    private volatile long sleepMillis = 0;

    private CapabilityProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(CapabilitySidecarClient.TABLE_PATH, this::handle);
        server.start();
        properties = new CapabilityProperties();
        properties.setSidecarBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/");
        properties.setConnectTimeoutMillis(500);
        properties.setReadTimeoutMillis(300);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    /** 正常链路：请求体带区域契约字段，响应解析成 TableResult（含格级 bbox 与置信度） */
    @Test
    void postShouldSendRegionAndParseTableResult() {
        responseBody = "{\"rows\":1,\"cols\":1,\"confidence\":0.9,\"model\":\"tableformer\",\"version\":\"1.0\","
                + "\"cells\":[{\"row\":0,\"col\":0,\"text\":\"合计\",\"isHeader\":true,"
                + "\"bbox\":{\"x\":72.0,\"y\":100.0,\"width\":300.0,\"height\":20.0},\"confidence\":0.95}]}";
        TableRegion region = new TableRegion();
        region.setPage(2);
        region.setBbox(new BBox(72, 100, 300, 120));

        TableResult result = new CapabilitySidecarClient(properties)
                .post(CapabilitySidecarClient.TABLE_PATH, region, TableResult.class);

        assertNotNull(result);
        assertEquals(1, result.getRows());
        assertEquals("tableformer", result.getModel());
        assertEquals(0.95, result.getCells().getFirst().getConfidence(), 0.0001);
        assertEquals(300.0, result.getCells().getFirst().getBbox().getWidth(), 0.0001);
        assertTrue(lastRequestBody.get().contains("\"page\":2"), () -> "body=" + lastRequestBody.get());
        assertTrue(lastRequestBody.get().contains("\"width\":300"), () -> "body=" + lastRequestBody.get());
    }

    /** 非 2xx：返回 null，不抛异常 */
    @Test
    void nonSuccessStatusShouldReturnNull() {
        statusCode = 500;

        assertNull(new CapabilitySidecarClient(properties)
                .post(CapabilitySidecarClient.TABLE_PATH, new TableRegion(), TableResult.class));
    }

    /** 读超时：返回 null（按能力失败降级） */
    @Test
    void readTimeoutShouldReturnNull() {
        sleepMillis = 1000;

        assertNull(new CapabilitySidecarClient(properties)
                .post(CapabilitySidecarClient.TABLE_PATH, new TableRegion(), TableResult.class));
    }

    /** 未配置地址：不发起调用，直接返回 null */
    @Test
    void unconfiguredSidecarShouldReturnNullWithoutCall() {
        properties.setSidecarBaseUrl("");
        CapabilitySidecarClient client = new CapabilitySidecarClient(properties);

        assertFalse(client.configured());
        assertNull(client.post(CapabilitySidecarClient.TABLE_PATH, new TableRegion(), TableResult.class));
        assertNull(lastRequestBody.get());
    }

    /** 假 sidecar：记录请求体、按设定状态码与响应体作答（可选延迟模拟超时） */
    private void handle(HttpExchange exchange) throws IOException {
        lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        if (sleepMillis > 0) {
            try {
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
