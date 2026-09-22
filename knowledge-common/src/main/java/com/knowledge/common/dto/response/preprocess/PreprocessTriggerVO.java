package com.knowledge.common.dto.response.preprocess;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 触发预处理响应 VO：任务触发结果的对外视图（任务 ID + 生效策略版本，触发时固定）。
 *
 * @author cxxl
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreprocessTriggerVO {

    /** 文件结果 ID（kb_file_result.id） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileResultId;

    /** 处理链任务 ID（kb_pipeline_task.id） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long pipelineTaskId;

    /** 生效策略版本（如 preproc-default-v1） */
    private String strategyVersion;
}
