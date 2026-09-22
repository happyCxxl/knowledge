package com.knowledge.worker.parser.capability;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 模型能力开关（knowledge.parse.capability 前缀；一期全部默认 false）。
 * 开关只控制"已注册实现是否启用"；无实现时恒走内置降级。
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
}
