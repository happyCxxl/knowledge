package com.knowledge.common.dto.request.strategy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 策略版本创建请求：type/name/version/configSnapshot 必填；configSnapshot 为 JSON 文本（规则开关）。
 *
 * @author cxxl
 */
@Data
public class StrategyVersionCreateDto {

    /** 策略类型（PREPROCESS/CHUNK/EMBED/RETRIEVAL，白名单校验） */
    @NotBlank(message = "type 不能为空")
    private String type;

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
