package com.knowledge.common.dto.response.lineage;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 执行树节点 = 一次运行（节点是运行不是策略，同策略多跑 = 多个节点）。
 *
 * @author cxxl
 */
@Data
public class LineageNodeVO {

    /** 任务 ID（雪花转字符串） */
    private Long taskId;

    /** 环节（PipelineStage 枚举名） */
    private String stage;

    /** 任务状态 */
    private String status;

    /** 错误码 / 错误信息（失败时非空） */
    private String errorCode;

    private String errorMsg;

    /** 策略版本（PREPROCESS/CHUNK/EMBED 有，name-version 串；PARSE/STRUCTURE 为空） */
    private String strategyVersion;

    /**
     * 能力快照（无策略环节展示用：PARSE/STRUCTURE 有，其余环节为空）。
     *
     * <p>由服务层把 {@code product.capabilitySnapshot} 的 JSON 文本解析成
     * {@link LineageCapabilityVO} 后返回；JSON 无法解析时本字段为 null（不回落原文，
     * 避免把脏数据继续往上层传）。
     */
    private LineageCapabilityVO capability;

    /** 产物 ID（成功任务对应产物；无产物为空）。触发下游环节时传此值（非任务 ID） */
    private Long productId;

    /** 产物引用（成功有产物时非空） */
    private String artifactId;

    /** 产物内容指纹 */
    private String contentHash;

    /** 统计摘要（展示用）：CHUNK=chunkCount；EMBED=recordCount/cachedCount；PREPROCESS=matched/changed（step_log 聚合）；PARSE/STRUCTURE 为空 */
    private Map<String, String> stats;

    /** 任务时间 */
    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
