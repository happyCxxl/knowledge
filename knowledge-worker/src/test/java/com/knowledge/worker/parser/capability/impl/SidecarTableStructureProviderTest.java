package com.knowledge.worker.parser.capability.impl;

import com.knowledge.common.domain.parse.BBox;
import com.knowledge.common.domain.parse.capability.TableRegion;
import com.knowledge.common.domain.parse.capability.TableResult;
import com.knowledge.worker.parser.capability.CapabilityProperties;
import com.knowledge.worker.parser.capability.http.CapabilitySidecarClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 表格结构识别 sidecar 实现单测（假 sidecar = JDK HttpServer）：开关开且地址就绪时按契约取回结构；
 * 开关关或未配置地址时返回 null 且不发起调用。
 *
 * @author cxxl
 */
class SidecarTableStructureProviderTest {

    private HttpServer server;
    private CapabilityProperties properties;
    private SidecarTableStructureProvider provider;
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(CapabilitySidecarClient.TABLE_PATH, this::handle);
        server.start();
        properties = new CapabilityProperties();
        properties.setSidecarBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setTableEnabled(true);
        provider = new SidecarTableStructureProvider(properties, new CapabilitySidecarClient(properties));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    /** 开关开 + 地址就绪：取回 grid、合并跨度与格级置信度 */
    @Test
    void enabledProviderShouldReturnStructure() {
        TableResult result = provider.recognize(region(), null);

        assertNotNull(result);
        assertEquals(2, result.getRows());
        assertEquals(2, result.getCells().getFirst().getColSpan());
        assertEquals(0.97, result.getCells().getFirst().getConfidence(), 0.0001);
        assertEquals(1, calls.get());
    }

    /** 开关关：返回 null 且不发起调用 */
    @Test
    void disabledProviderShouldReturnNullWithoutCall() {
        properties.setTableEnabled(false);

        assertNull(provider.recognize(region(), null));
        assertEquals(0, calls.get());
    }

    /** 开关开但地址未配置：返回 null 且不发起调用 */
    @Test
    void unconfiguredSidecarShouldReturnNullWithoutCall() {
        properties.setSidecarBaseUrl("");

        assertNull(provider.recognize(region(), null));
        assertEquals(0, calls.get());
    }

    private TableRegion region() {
        TableRegion region = new TableRegion();
        region.setPage(1);
        region.setBbox(new BBox(72, 100, 300, 120));
        return region;
    }

    /** 假 sidecar：按契约返回一张 2×2 表（首格横向合并、带置信度） */
    private void handle(HttpExchange exchange) throws IOException {
        calls.incrementAndGet();
        String body = "{\"rows\":2,\"cols\":2,\"model\":\"tableformer\",\"version\":\"1.0\",\"cells\":["
                + "{\"row\":0,\"col\":0,\"colSpan\":2,\"text\":\"合计\",\"isHeader\":true,\"confidence\":0.97},"
                + "{\"row\":1,\"col\":0,\"text\":\"A1\"}]}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
