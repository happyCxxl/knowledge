package com.knowledge.common.dto.response.chunk;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 触发切片响应：任务 ID + 生效策略版本（触发时固定）。
 *
 * @author cxxl
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkTriggerVO {

    /** 文件结果 ID（kb_file_result.id） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileResultId;

    /** 处理链任务 ID（kb_pipeline_task.id） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long pipelineTaskId;

    /** 生效策略版本（如 chunk-hybrid-v1） */
    private String strategyVersion;
}
