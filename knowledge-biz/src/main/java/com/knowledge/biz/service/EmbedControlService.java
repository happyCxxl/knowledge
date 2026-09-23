package com.knowledge.biz.service;

import com.knowledge.common.dto.response.embed.EmbedDetailVO;
import com.knowledge.common.dto.response.embed.EmbedTriggerVO;

/**
 * 向量化控制面服务（手动逐环节，与切片/预处理同构）。
 *
 * @author cxxl
 */
public interface EmbedControlService {

    /**
     * 触发向量化/重跑：策略解析（传参校验 / KB 绑定 / 全局最新启用 / 内置默认）→ 上游 CHUNK 产物校验 →
     * 窗口前置校验 → 防重复用 → 建任务入队。
     */
    EmbedTriggerVO embed(Long fileResultId, Long strategyVersionId, Long upstreamProductId);

    /**
     * 向量化详情：任务状态 + 子步骤 + 集合摘要/记录 + 产物引用；taskId 可选（缺省最新任务）。
     */
    EmbedDetailVO embedDetail(Long fileResultId, Long taskId);
}
