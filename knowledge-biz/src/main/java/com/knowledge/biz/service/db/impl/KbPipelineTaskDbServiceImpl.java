package com.knowledge.biz.service.db.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.knowledge.biz.mapper.KbPipelineTaskMapper;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.infra.persistence.InfraDbServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 处理链任务数据访问服务实现。
 *
 * @author cxxl
 */
@Service
public class KbPipelineTaskDbServiceImpl extends InfraDbServiceImpl<KbPipelineTaskMapper, KbPipelineTask>
        implements KbPipelineTaskDbService {

    @Override
    public KbPipelineTask getByFileResultIdAndStage(Long fileResultId, String stage) {
        LambdaQueryWrapper<KbPipelineTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KbPipelineTask::getFileResultId, fileResultId)
                .eq(KbPipelineTask::getStage, stage)
                .orderByDesc(KbPipelineTask::getId)
                .last("LIMIT 1");
        return getOne(queryWrapper, false);
    }

    @Override
    public List<KbPipelineTask> listByFileResultIdsAndStage(List<Long> fileResultIds, String stage) {
        if (fileResultIds == null || fileResultIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<KbPipelineTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(KbPipelineTask::getFileResultId, fileResultIds)
                .eq(KbPipelineTask::getStage, stage)
                .orderByDesc(KbPipelineTask::getId);
        return list(queryWrapper);
    }

    @Override
    public List<KbPipelineTask> listStaleQueued(LocalDateTime threshold) {
        LambdaQueryWrapper<KbPipelineTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KbPipelineTask::getStatus, PipelineTaskStatus.QUEUED.name())
                .lt(KbPipelineTask::getCreateTime, threshold);
        return list(queryWrapper);
    }

    @Override
    public List<KbPipelineTask> listStaleRunning(LocalDateTime threshold) {
        LambdaQueryWrapper<KbPipelineTask> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KbPipelineTask::getStatus, PipelineTaskStatus.RUNNING.name())
                .lt(KbPipelineTask::getStartedAt, threshold);
        return list(queryWrapper);
    }

    @Override
    public int finish(Long id, String status, String errorCode, String errorMsg) {
        LambdaUpdateWrapper<KbPipelineTask> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(KbPipelineTask::getId, id)
                .eq(KbPipelineTask::getStatus, PipelineTaskStatus.RUNNING.name())
                .set(KbPipelineTask::getStatus, status)
                .set(KbPipelineTask::getErrorCode, errorCode)
                .set(KbPipelineTask::getErrorMsg, errorMsg)
                .set(KbPipelineTask::getFinishedAt, LocalDateTime.now());
        return baseMapper.update(null, updateWrapper);
    }
}
