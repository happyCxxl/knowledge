package com.knowledge.biz.task;

import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.common.domain.task.StageOutcome;
import com.knowledge.common.enums.task.PipelineTaskStatus;

/**
 * 环节任务终态收尾共用助手（各环节任务执行器共用）：
 * 成功/部分成功 → 成功收尾（产物落库）后回写终态；其余 → 子步骤落库 + 失败回写。
 * 手动逐环节口径：成功后停在终态，不自动触发下游。
 *
 * @author cxxl
 */
public final class TaskRunnerSupport {

    private TaskRunnerSupport() {
    }

    /** 终态收尾：按建议状态分派成功/失败路径。 */
    public static void complete(KbPipelineTaskDbService pipelineTaskDbService,
                                StepLogPersistence stepLogPersistence, Long taskId,
                                StageOutcome outcome, Runnable onSuccess, FailedFinisher onFailure) {
        String suggested = outcome.getSuggestedStatus();
        if (PipelineTaskStatus.SUCCESS.name().equals(suggested)
                || PipelineTaskStatus.PARTIAL_SUCCESS.name().equals(suggested)) {
            onSuccess.run();
            pipelineTaskDbService.finish(taskId, suggested, null, null);
        } else {
            stepLogPersistence.save(taskId, outcome.getStepLogs());
            onFailure.finish(taskId, outcome.getErrorCode(), outcome.getErrorMsg());
        }
    }

    /** 失败回写动作（各执行器自持回写口径）。 */
    @FunctionalInterface
    public interface FailedFinisher {

        void finish(Long taskId, String errorCode, String errorMsg);
    }
}
