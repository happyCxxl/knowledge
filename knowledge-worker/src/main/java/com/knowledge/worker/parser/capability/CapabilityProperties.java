package com.knowledge.worker.parser.capability;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 模型能力开关与 sidecar 接入参数（knowledge.parse.capability 前缀；三项开关默认 false）。
 * 开关只控制"已注册实现是否启用"；无实现、sidecar 地址为空或调用失败时恒走内置降级。
 *
 * @author cxxl
 */
@Data
@Component
@ConfigurationProperties(prefix = "knowledge.parse.capability")
public class CapabilityProperties {

    /** 结构化 OCR 开关（预留，默认关） */
    private boolean ocrEnabled = false;

    /** 版面分析开关（预留，默认关） */
    private boolean layoutEnabled = false;

    /** 表格结构识别开关（预留，默认关） */
    private boolean tableEnabled = false;

    /** sidecar 服务地址（空 = 未配置，只对规则失败页与表发起的调用全部省掉） */
    private String sidecarBaseUrl = "";

    /** sidecar 建连超时（毫秒） */
    private int connectTimeoutMillis = 1000;

    /** sidecar 读超时（毫秒；超时按能力失败处理并留痕降级） */
    private int readTimeoutMillis = 5000;
}
