package com.knowledge.common.dto.request.strategy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 策略编辑请求（B0 策略版本行不可变：编辑 = 复制新版本——旧行原样保留，本 DTO 承载新行的 name/version/configSnapshot）。
 *
 * @author cxxl
 */
@Data
public class StrategyVersionUpdateDto {

    /** 策略名 */
    @NotBlank(message = "name 不能为空")
    @Size(max = 128, message = "name 最长 128 字符")
    private String name;

    /** 版本号 */
    @NotBlank(message = "version 不能为空")
    @Size(max = 32, message = "version 最长 32 字符")
    private String version;

    /** 配置快照（JSON 文本） */
    @NotBlank(message = "configSnapshot 不能为空")
    private String configSnapshot;
}
