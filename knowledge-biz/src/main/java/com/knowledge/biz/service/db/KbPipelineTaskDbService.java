package com.knowledge.biz.service.db;

import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.infra.persistence.InfraDbService;

import java.time.LocalDateTime;
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

    /**
     * 查过期未执行的 QUEUED 任务（启动补偿/低频兜底用）。
     *
     * @param threshold 创建时间阈值（早于该时间视为过期）
     * @return QUEUED 且创建时间早于阈值的任务列表
     */
    List<KbPipelineTask> listStaleQueued(LocalDateTime threshold);

    /**
     * 查孤儿执行中任务（孤儿恢复用）。
     *
     * @param threshold 开始时间阈值（早于该时间视为孤儿）
     * @return RUNNING 且开始时间早于阈值的任务列表
     */
    List<KbPipelineTask> listStaleRunning(LocalDateTime threshold);

    /**
     * 终态回写：RUNNING → 指定终态 + 结束时间 + 错误码/信息（条件更新，后到者得 0）。
     *
     * @param id        任务 ID
     * @param status    目标终态（PipelineTaskStatus 枚举名）
     * @param errorCode 错误码（失败时传入；成功为 null）
     * @param errorMsg  错误信息（失败时传入；成功为 null）
     * @return 受影响行数
     */
    int finish(Long id, String status, String errorCode, String errorMsg);
}
