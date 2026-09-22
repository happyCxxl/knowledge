package com.knowledge.biz.service.db;

import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.infra.persistence.InfraDbService;

import java.util.List;

/**
 * 处理链任务数据访问服务（kb_pipeline_task）。
 *
 * @author cxxl
 */
public interface KbPipelineTaskDbService extends InfraDbService<KbPipelineTask> {

    /**
     * 按文件结果 + 环节查最新任务（id 倒序取一条）。
     *
     * @param fileResultId 文件结果 ID
     * @param stage        环节（PipelineStage 枚举名）
     * @return 任务实体；不存在返回 null
     */
    KbPipelineTask getByFileResultIdAndStage(Long fileResultId, String stage);

    /**
     * 批量查指定环节任务（列表行环节状态用）：fileResultIds 内 + stage，id 倒序
     * （调用方按 fileResultId 去重取第一条即最新任务）。
     *
     * @param fileResultIds 文件结果 ID 列表（为空返回空列表，不查库）
     * @param stage         环节（PipelineStage 枚举名）
     * @return 任务列表
     */
    List<KbPipelineTask> listByFileResultIdsAndStage(List<Long> fileResultIds, String stage);
}
