package com.knowledge.common.enums.task;

/**
 * 处理链任务状态：
 * QUEUED → RUNNING → SUCCESS / PARTIAL_SUCCESS / FAILED（CREATED 为入队前瞬时态，不落库）。
 * 失败即该环节终局，不设取消态：链路只由输入与策略推导，状态不参与链路决策。
 *
 * @author cxxl
 */
public enum PipelineTaskStatus {

    /** 已入队待执行（建档 INSERT 即此态） */
    QUEUED,

    /** 执行中（执行器条件更新领取后） */
    RUNNING,

    /** 成功 */
    SUCCESS,

    /** 部分成功（部分元素/页面成功，保留告警） */
    PARTIAL_SUCCESS,

    /** 失败（保留错误与已生成产物，失败不覆盖成功） */
    FAILED
}
