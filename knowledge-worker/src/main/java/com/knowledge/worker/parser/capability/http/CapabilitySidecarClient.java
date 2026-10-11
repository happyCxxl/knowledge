package com.knowledge.worker.parser.capability.http;

import cn.hutool.core.util.StrUtil;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.worker.parser.capability.CapabilityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 能力 sidecar 传输层：按能力路径把请求体 POST 过去、按契约反序列化响应。
 * 未配置地址、非 2xx、超时或被中断一律返回 null，取用方按「能力失败」走内置降级并留痕。
 *
 * @author cxxl
 */
@Slf4j
@Component
public class CapabilitySidecarClient {

    /** 表格结构识别路径（与 sidecar 约定的能力路径） */
    public static final String TABLE_PATH = "/table";

    private final CapabilityProperties properties;
    private final HttpClient httpClient;

    public CapabilitySidecarClient(CapabilityProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .build();
    }

    /** 是否已配置 sidecar 地址（未配置即不发起任何调用） */
    public boolean configured() {
        return StrUtil.isNotBlank(properties.getSidecarBaseUrl());
    }

    /**
     * POST 调用并按契约反序列化。
     *
     * @param path         能力路径（如 {@link #TABLE_PATH}）
     * @param request      请求体（能力契约 DTO）
     * @param responseType 响应类型（能力契约 DTO）
     * @return 响应对象；未配置 / 非 2xx / 超时 / 中断 / 解析失败时为 null
     */
    public <T> T post(String path, Object request, Class<T> responseType) {
        if (!configured()) {
            return null;
        }
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + path))
                    .timeout(Duration.ofMillis(properties.getReadTimeoutMillis()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(JsonUtil.toJsonStr(request)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("能力 sidecar 返回非成功状态, path={}, status={}", path, response.statusCode());
                return null;
            }
            return JsonUtil.toObject(response.body(), responseType);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("能力 sidecar 调用被中断, path={}", path);
            return null;
        } catch (Exception e) {
            log.warn("能力 sidecar 调用失败, path={}", path, e);
            return null;
        }
    }

    /** 基地址（去掉尾部斜杠） */
    private String baseUrl() {
        String base = properties.getSidecarBaseUrl().trim();
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
