package com.knowledge.common.dto.response.task;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 环节详情视图公共字段：文件结果与任务状态摘要、产物引用、子步骤列表。
 * 各环节详情 VO 继承本类并追加环节专属统计/列表字段。
 */
@Data
public class StageDetailVO implements TaskStatusView {

    /** 文件结果 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileResultId;

    /** 任务 ID（kb_pipeline_task.id；无任务时为空） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** 环节 */
    private String stage;

    /** 任务状态（PipelineTaskStatus 枚举名） */
    private String status;

    /** 任务错误码（PipelineTaskErrorCode 枚举名） */
    private String errorCode;

    /** 任务错误信息 */
    private String errorMsg;

    /** 任务开始时间 */
    private LocalDateTime startedAt;

    /** 任务结束时间 */
    private LocalDateTime finishedAt;

    /** 产物引用（sha256 寻址 key；失败无产物时为空） */
    private String artifactId;

    /** 产物内容指纹 */
    private String contentHash;

    /** 能力快照（JSON 文本） */
    private String capabilitySnapshot;

    /** 子步骤列表 */
    private List<StepLogVO> steps;
}
