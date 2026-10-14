package com.knowledge.common.dto.response.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 连通性测试结果 VO：一次最小真实请求的可用性、延迟与响应回传的模型标识。
 *
 * <p>请求发出后的失败同样落这一份结果（`available` 为 false，`errorCode` 与 `message` 给出可读原因），
 * 结论的一行文本另经 `probeResult` 回写模型行；两类结论都刷新 `probeAt`。
 *
 * @author cxxl
 */
@Data
public class ModelProbeVO {

    /** 是否可用（本次最小真实请求是否成功返回） */
    private boolean available;

    /** 本次调用耗时（毫秒；失败时给出到失败为止的耗时） */
    private long latencyMs;

    /** 本次探测用的模态（CHAT / EMBEDDING / OCR / LAYOUT / TABLE_STRUCTURE） */
    private String capability;

    /** 响应回传的模型标识（失败为空） */
    private String model;

    /** 响应回传的模型版本（响应不带时为空） */
    private String modelVersion;

    /** 失败时的错误码名（成功为空） */
    private String errorCode;

    /** 失败原因（成功为空；失败时给出可读消息） */
    private String message;

    /** 本次探测时间 */
    private LocalDateTime probeAt;

    /** 回写到模型行的结论一行文本 */
    private String probeResult;
}
