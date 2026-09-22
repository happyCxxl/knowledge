package com.knowledge.common.enums.task;

/**
 * 任务错误码：落 kb_pipeline_task.error_code，与 API 错误码（ErrorCode 40xxx）分层。
 *
 * @author cxxl
 */
public enum PipelineTaskErrorCode {

    /** 执行超时（孤儿恢复判定） */
    EXECUTOR_TIMEOUT
}
