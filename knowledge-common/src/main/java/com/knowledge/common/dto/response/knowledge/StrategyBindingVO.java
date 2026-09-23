package com.knowledge.common.dto.response.knowledge;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 知识库-策略绑定视图（step-09）：未绑定时仅返回 strategyType。
 *
 * @author cxxl
 */
@Data
public class StrategyBindingVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 策略类型：PREPROCESS / CHUNK */
    private String strategyType;

    /** 绑定的策略版本行 ID（雪花 ID） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long strategyVersionId;

    /** 绑定策略名 */
    private String strategyName;

    /** 绑定策略版本号 */
    private String strategyVersion;
}
