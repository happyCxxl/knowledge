package com.knowledge.common.dto.response.structure;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.knowledge.common.dto.response.task.StepLogVO;
import com.knowledge.common.dto.response.task.TaskStatusView;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 组装详情 VO：工作台"组装详情"页数据源。
 *
 * @author cxxl
 */
@Data
public class StructureDetailVO implements TaskStatusView {

    /** 文件结果 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileResultId;

    /** 任务 ID（kb_pipeline_task.id；无任务时为空） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** 环节（STRUCTURE） */
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

    /** 组装统计（从统一文档产物推导；无产物时为空） */
    private StructureSummaryVO summary;

    /** 质量告警（从产物 JSON 提取；无产物时为空列表） */
    private List<String> warnings;

    /** 冲突记录（PRIMARY/BACKUP 并存方案；无产物时为空列表） */
    private List<StructureConflictVO> conflicts;

    /** 文档内容大纲（元素按阅读顺序全量，含标题/段落/表格等；无产物时为空列表） */
    private List<StructureOutlineVO> outline;
}
