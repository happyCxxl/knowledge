package com.knowledge.common.dto.response.index;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 触发索引构建响应（step-13 B08）：版本行 + 构建任务 ID（页面据此轮询构建状态）。
 *
 * @author cxxl
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IndexBuildTriggerVO {

    /** 索引版本行 ID（kb_index_version.id） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long versionId;

    /** 版本号（v1、v2…） */
    private String versionNo;

    /** 构建任务 ID（kb_pipeline_task.id，stage=BUILD_INDEX） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long pipelineTaskId;
}
