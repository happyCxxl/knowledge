package com.knowledge.common.dto.response.lineage;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 执行树血缘边：上游任务 → 下游任务。
 * 边来源：下游任务 upstreamProductId → 产物行 → 反查产出该产物的任务（task.productId）。
 *
 * @author cxxl
 */
@Data
public class LineageEdgeVO {

    /** 上游任务 ID（雪花转字符串） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromTaskId;

    /** 下游任务 ID（雪花转字符串） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long toTaskId;
}
