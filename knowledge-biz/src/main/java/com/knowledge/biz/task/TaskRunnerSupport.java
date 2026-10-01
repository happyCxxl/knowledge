package com.knowledge.biz.task;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.task.StageOutcome;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.PipelineTaskStatus;

/**
 * 环节任务执行器共用助手：**领任务的准入判断**、**上游产物解析**、**终态收尾**三件事各环节完全同构，
 * 集中在这里，执行器只管自己的管线调用与落库。
 *
 * <p>手动逐环节口径：成功后停在终态，不自动触发下游。
 *
 * @author cxxl
 */
public final class TaskRunnerSupport {

    /** 失败原因入库前的截断长度（字符） */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private TaskRunnerSupport() {
    }

    /**
     * 领任务：不存在、非 QUEUED、或条件更新（{@code claim}）未抢到时返回 null；
     * 条件更新是多实例下的互斥闸门。
     *
     * @param pipelineTaskDbService 任务数据访问
     * @param taskId                任务 ID
     * @return 领取到的任务；未领取到返回 null
     */
    public static KbPipelineTask claim(KbPipelineTaskDbService pipelineTaskDbService, Long taskId) {
        KbPipelineTask task = pipelineTaskDbService.getById(taskId);
        if (ObjectUtil.isNull(task)
                || !PipelineTaskStatus.QUEUED.name().equals(task.getStatus())) {
            return null;
        }
        return pipelineTaskDbService.claim(taskId) == 1 ? task : null;
    }

    /**
     * 上游产物解析：任务指定 {@code upstreamProductId} 优先，缺省或查不到时回退该环节最新产物。
     *
     * @param productDbService 产物数据访问
     * @param task             当前任务
     * @param stage            上游环节（PARSE / STRUCTURE / PREPROCESS / CHUNK）
     * @return 上游产物；两者都没有时返回 null
     */
    public static KbPipelineProduct resolveUpstreamProduct(KbPipelineProductDbService productDbService,
                                                           KbPipelineTask task, PipelineStage stage) {
        KbPipelineProduct product = ObjectUtil.isNull(task.getUpstreamProductId()) ? null
                : productDbService.getById(task.getUpstreamProductId());
        return ObjectUtil.isNull(product)
                ? productDbService.getByFileResultIdAndStage(task.getFileResultId(), stage.name())
                : product;
    }

    /**
     * 失败回写：把任务置 FAILED 并落失败原因（原因统一截断到 {@value #MAX_ERROR_MESSAGE_LENGTH} 字符，
     * 空串按 null 落库）。
     *
     * @param pipelineTaskDbService 任务数据访问
     * @param taskId                任务 ID
     * @param errorCode             错误码（各环节自己的枚举码值）
     * @param errorMsg              失败原因（可空；超长截断，空串按 null 落库）
     */
    public static void finishFailed(KbPipelineTaskDbService pipelineTaskDbService, Long taskId,
                                    String errorCode, String errorMsg) {
        pipelineTaskDbService.finish(taskId, PipelineTaskStatus.FAILED.name(), errorCode,
                StrUtil.isBlank(errorMsg) ? null : StrUtil.maxLength(errorMsg, MAX_ERROR_MESSAGE_LENGTH));
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
