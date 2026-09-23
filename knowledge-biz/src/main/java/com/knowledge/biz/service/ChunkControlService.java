package com.knowledge.biz.service;

import com.knowledge.common.dto.response.chunk.ChunkDetailVO;
import com.knowledge.common.dto.response.chunk.ChunkTriggerVO;

/**
 * 切片控制面：触发切片/重跑（手动逐环节）+ 切片详情。
 *
 * @author cxxl
 */
public interface ChunkControlService {

    /**
     * 触发切片（strategyVersionId 可选：策略行 ID，缺省取库内启用中最新版本，库内无回退内置默认）。
     *
     * @param fileResultId       文件结果 ID
     * @param strategyVersionId  策略版本行 ID（可空）
     * @param upstreamProductId 上游产物 ID（可选，指定 PREPROCESS 产物，缺省取最新）
     * @return 任务 ID + 生效策略版本
     */
    ChunkTriggerVO chunk(Long fileResultId, Long strategyVersionId, Long upstreamProductId);

    /**
     * 切片详情：任务状态 + 子步骤 + 切片统计/切片列表/产物引用（来自最新切片集合与 kb_chunk）；
     * taskId 可选（缺省取最新任务，传了则查该次运行）。
     */
    ChunkDetailVO chunkDetail(Long fileResultId, Long taskId);
}
