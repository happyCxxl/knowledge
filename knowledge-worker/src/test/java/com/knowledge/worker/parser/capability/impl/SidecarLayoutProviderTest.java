package com.knowledge.worker.parser.capability.impl;

import com.knowledge.common.domain.parse.BBox;
import com.knowledge.common.domain.parse.capability.LayoutResult;
import com.knowledge.common.domain.parse.capability.PageImage;
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
 * 版面分析 sidecar 实现单测（假 sidecar = JDK HttpServer）：开关开且地址就绪时取回区域分类与阅读顺序；
 * 开关关或未配置地址时返回 null 且不发起调用。
 *
 * @author cxxl
 */
class SidecarLayoutProviderTest {

    private HttpServer server;
    private CapabilityProperties properties;
    private SidecarLayoutProvider provider;
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(CapabilitySidecarClient.LAYOUT_PATH, this::handle);
        server.start();
        properties = new CapabilityProperties();
        properties.setSidecarBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setLayoutEnabled(true);
        provider = new SidecarLayoutProvider(properties, new CapabilitySidecarClient(properties));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    /** 开关开 + 地址就绪：取回区域分类、阅读顺序与模型版本 */
    @Test
    void enabledProviderShouldReturnLayout() {
        LayoutResult result = provider.analyze(page(), null);

        assertNotNull(result);
        assertEquals(2, result.getRegions().size());
        assertEquals("title", result.getRegions().getFirst().getType());
        assertEquals(0.98, result.getRegions().getFirst().getConfidence(), 0.0001);
        assertEquals(1, result.getOrder().getFirst());
        assertEquals("layout-model", result.getModel());
        assertEquals(1, calls.get());
    }

    /** 开关关：返回 null 且不发起调用 */
    @Test
    void disabledProviderShouldReturnNullWithoutCall() {
        properties.setLayoutEnabled(false);

        assertNull(provider.analyze(page(), null));
        assertEquals(0, calls.get());
    }

    /** 开关开但地址未配置：返回 null 且不发起调用 */
    @Test
    void unconfiguredSidecarShouldReturnNullWithoutCall() {
        properties.setSidecarBaseUrl("");

        assertNull(provider.analyze(page(), null));
        assertEquals(0, calls.get());
    }

    private PageImage page() {
        PageImage image = new PageImage();
        image.setPage(1);
        image.setDpi(150);
        image.setWidth(595.0);
        image.setHeight(842.0);
        return image;
    }

    /** 假 sidecar：按契约返回两个区域与阅读顺序 */
    private void handle(HttpExchange exchange) throws IOException {
        calls.incrementAndGet();
        String body = "{\"model\":\"layout-model\",\"version\":\"2.0\",\"order\":[1,0],\"regions\":["
                + "{\"type\":\"title\",\"confidence\":0.98,\"bbox\":{\"x\":72.0,\"y\":60.0,\"width\":300.0,"
                + "\"height\":24.0}},"
                + "{\"type\":\"text\",\"confidence\":0.91,\"bbox\":{\"x\":72.0,\"y\":100.0,\"width\":450.0,"
                + "\"height\":200.0}}]}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
