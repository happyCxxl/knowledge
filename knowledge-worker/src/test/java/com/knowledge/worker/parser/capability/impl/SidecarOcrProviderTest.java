package com.knowledge.worker.parser.capability.impl;

import com.knowledge.common.domain.parse.BBox;
import com.knowledge.common.domain.parse.capability.OcrRegion;
import com.knowledge.common.domain.parse.capability.StructuredOcrResult;
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
 * 结构化 OCR sidecar 实现单测（假 sidecar = JDK HttpServer）：开关开且地址就绪时取回逐行文本、
 * 包围盒与置信度；开关关或未配置地址时返回 null 且不发起调用。
 *
 * @author cxxl
 */
class SidecarOcrProviderTest {

    private HttpServer server;
    private CapabilityProperties properties;
    private SidecarOcrProvider provider;
    private final AtomicInteger calls = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(CapabilitySidecarClient.OCR_PATH, this::handle);
        server.start();
        properties = new CapabilityProperties();
        properties.setSidecarBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setOcrEnabled(true);
        provider = new SidecarOcrProvider(properties, new CapabilitySidecarClient(properties));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    /** 开关开 + 地址就绪：取回两行文本、包围盒、置信度与模型版本 */
    @Test
    void enabledProviderShouldReturnLines() {
        StructuredOcrResult result = provider.recognize(region(), null);

        assertNotNull(result);
        assertEquals(2, result.getLines().size());
        assertEquals("invoice no", result.getLines().getFirst().getText());
        assertEquals(0.94, result.getLines().getFirst().getConfidence(), 0.0001);
        assertEquals(120.0, result.getLines().getFirst().getBbox().getWidth(), 0.0001);
        assertEquals("ocr-model", result.getModel());
        assertEquals("1.2", result.getVersion());
        assertEquals(1, calls.get());
    }

    /** 开关关：返回 null 且不发起调用 */
    @Test
    void disabledProviderShouldReturnNullWithoutCall() {
        properties.setOcrEnabled(false);

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

    private OcrRegion region() {
        OcrRegion region = new OcrRegion();
        region.setPage(1);
        region.setImageRef("page1-image0");
        region.setBbox(new BBox(72, 100, 300, 120));
        return region;
    }

    /** 假 sidecar：按契约返回两行识别结果 */
    private void handle(HttpExchange exchange) throws IOException {
        calls.incrementAndGet();
        String body = "{\"model\":\"ocr-model\",\"version\":\"1.2\",\"lines\":["
                + "{\"text\":\"invoice no\",\"confidence\":0.94,\"bbox\":{\"x\":72.0,\"y\":100.0,"
                + "\"width\":120.0,\"height\":12.0}},"
                + "{\"text\":\"total 30\",\"confidence\":0.88,\"bbox\":{\"x\":72.0,\"y\":120.0,"
                + "\"width\":90.0,\"height\":12.0}}]}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
