package com.knowledge.common.enums.task;

/**
 * 任务错误码：落 kb_pipeline_task.error_code，与 API 错误码（ErrorCode 40xxx）分层。
 *
 * @author cxxl
 */
public enum PipelineTaskErrorCode {

    /** 执行超时（孤儿恢复判定） */
    EXECUTOR_TIMEOUT,

    /** 文件深层损坏（入口探测未发现，解析时暴露） */
    PARSE_CORRUPTED,

    /** 扫描件暂不支持（OCR 预留，一期不接） */
    SCANNED_UNSUPPORTED,

    /** 成功单元占比低于门槛（90%） */
    RATIO_BELOW_THRESHOLD,

    /** 解析执行异常兜底 */
    PARSE_FAILED,

    /** 组装空树（上游产物缺失/无任何可组装元素） */
    STRUCTURE_EMPTY,

    /** 组装执行异常兜底 */
    STRUCTURE_FAILED
}
