package com.knowledge.common.enums.task;

/**
 * 处理链任务状态：
 * QUEUED → RUNNING → SUCCESS / PARTIAL_SUCCESS / FAILED / CANCELLED；
 * QUEUED 可直接 CANCELLED（CREATED 为入队前瞬时态，不落库）。
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
    FAILED,

    /** 已取消（管理员取消未开始或可取消的任务） */
    CANCELLED
}
