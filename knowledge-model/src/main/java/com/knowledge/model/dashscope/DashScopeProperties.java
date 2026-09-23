package com.knowledge.model.dashscope;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云 DashScope 向量化配置（knowledge.model.dashscope 前缀，Nacos 同名键可覆盖）。
 *
 * @author cxxl
 */
@Data
@Component
@ConfigurationProperties(prefix = "knowledge.model.dashscope")
public class DashScopeProperties {

    /** 服务地址（OpenAI 兼容模式） */
    private String baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    /** API Key（必填，运行时配置） */
    private String apiKey;

    /** 连接超时（毫秒） */
    private int connectTimeoutMs = 5000;

    /** 读超时（毫秒） */
    private int readTimeoutMs = 60000;
}
