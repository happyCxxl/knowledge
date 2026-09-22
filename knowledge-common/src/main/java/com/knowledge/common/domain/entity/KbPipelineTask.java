package com.knowledge.common.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 处理链任务（kb_pipeline_task）：一次环节触发一条，重跑 = 新记录。
 *
 * <p>机器表：无用户字段与更新留痕字段（仅 create_time）。
 *
 * @author cxxl
 */
@Data
@TableName("kb_pipeline_task")
public class KbPipelineTask {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属文件结果 */
    private Long fileResultId;

    /** 环节（PipelineStage 枚举名） */
    private String stage;

    /** 上游产物 ID（可空） */
    private Long upstreamProductId;

    /** 环节策略快照（JSON 文本，触发时固定） */
    private String strategySnapshot;

    /** 成功运行回写的产物 ID（kb_pipeline_product.id；以此产物触发下游的数据源） */
    private Long productId;

    /** 状态（PipelineTaskStatus 枚举名） */
    private String status;

    /** 重试次数 */
    private Integer retryCount;

    /** 错误码 */
    private String errorCode;

    /** 错误信息 */
    private String errorMsg;

    /** 创建时间（DB 默认填充） */
    private LocalDateTime createTime;

    /** 开始执行时间 */
    private LocalDateTime startedAt;

    /** 结束时间 */
    private LocalDateTime finishedAt;
}
