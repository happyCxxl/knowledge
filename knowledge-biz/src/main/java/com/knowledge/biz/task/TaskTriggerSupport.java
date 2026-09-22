package com.knowledge.biz.task;

import cn.hutool.core.util.ObjectUtil;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.dto.response.task.StageTriggerVO;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.ThrowUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 环节任务触发共用助手（手动逐环节触发接口族共用）：
 * 防重/补投唤醒 + 新建 QUEUED 任务入队，口径与各环节一致；环节特有预检由调用方完成。
 *
 * @author cxxl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskTriggerSupport {

    private final KbPipelineTaskDbService pipelineTaskDbService;
    private final TaskQueueSupport taskQueue;

    /**
     * 触发（RUNNING → 40431；QUEUED → 补投唤醒；banOnSuccess=true 时 SUCCESS/PARTIAL_SUCCESS → 40437；
     * 其余终态/无任务 → 新建 QUEUED 任务入队，旧任务/旧产物保留）。
     *
     * @param fileResultId     文件结果 ID
     * @param stage            环节（PipelineStage）
     * @param upstreamProductId 上游产物 ID（可空）
     * @param strategySnapshot 环节策略快照 JSON（可空；有策略的环节触发时固定，执行只用快照）
     * @param stageLabel       环节中文名（日志与错误提示用）
     * @param banOnSuccess     成功后是否禁止重跑
     * @return 触发响应（新登记/已有任务 ID）
     */
    public StageTriggerVO trigger(Long fileResultId, PipelineStage stage, Long upstreamProductId,
                                  String strategySnapshot, String stageLabel, boolean banOnSuccess) {
        KbPipelineTask existing = pipelineTaskDbService.getByFileResultIdAndStage(fileResultId, stage.name());
        if (ObjectUtil.isNotNull(existing)) {
            ThrowUtil.throwIf(PipelineTaskStatus.RUNNING.name().equals(existing.getStatus()),
                    ErrorCode.TASK_ALREADY_PENDING, stageLabel + "任务进行中，请勿重复触发");
            if (PipelineTaskStatus.QUEUED.name().equals(existing.getStatus())) {
                // 已登记未入队（登记与入队间崩溃的窗口）：直接入队唤醒，不重复建任务
                taskQueue.enqueue(existing.getId());
                log.info("===> TaskTriggerSupport trigger 唤醒待{}任务, fileResultId={}, taskId={}",
                        stageLabel, fileResultId, existing.getId());
                return new StageTriggerVO(existing.getId());
            }
            ThrowUtil.throwIf(banOnSuccess
                            && (PipelineTaskStatus.SUCCESS.name().equals(existing.getStatus())
                            || PipelineTaskStatus.PARTIAL_SUCCESS.name().equals(existing.getStatus())),
                    ErrorCode.PARSE_ALREADY_SUCCEEDED);
        }
        KbPipelineTask task = new KbPipelineTask();
        task.setFileResultId(fileResultId);
        task.setStage(stage.name());
        task.setStatus(PipelineTaskStatus.QUEUED.name());
        task.setRetryCount(0);
        task.setUpstreamProductId(upstreamProductId);
        task.setStrategySnapshot(strategySnapshot);
        pipelineTaskDbService.save(task);
        taskQueue.enqueue(task.getId());
        log.info("===> TaskTriggerSupport trigger 触发{}任务, fileResultId={}, taskId={}, upstream={}",
                stageLabel, fileResultId, task.getId(), upstreamProductId);
        return new StageTriggerVO(task.getId());
    }
}
