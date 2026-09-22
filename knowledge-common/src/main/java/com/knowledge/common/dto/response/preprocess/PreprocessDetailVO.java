package com.knowledge.common.dto.response.preprocess;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.knowledge.common.dto.response.task.StepLogVO;
import com.knowledge.common.dto.response.task.TaskStatusView;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 预处理详情 VO：工作台"预处理详情"页数据源。
 * 注：策略版本按运行记录展示（RunRecordVO.strategyVersion），不进详情。
 *
 * @author cxxl
 */
@Data
public class PreprocessDetailVO implements TaskStatusView {

    /** 文件结果 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileResultId;

    /** 任务 ID（kb_pipeline_task.id；无任务时为空） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** 环节（PREPROCESS） */
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

    /** 能力快照（JSON 文本，本环节=策略快照） */
    private String capabilitySnapshot;

    /** 子步骤列表 */
    private List<StepLogVO> steps;

    /** 预处理统计（从派生视图推导；无产物时为空） */
    private PreprocessSummaryVO summary;

    /** 视图元素（按阅读顺序；无产物时为空列表） */
    private List<PreprocessElementVO> elements;
}
