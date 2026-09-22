package com.knowledge.biz.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.knowledge.biz.service.ParseControlService;
import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineStepLogDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.biz.task.TaskQueueSupport;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineStepLog;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.parse.ParseResult;
import com.knowledge.common.dto.response.parse.ParseDetailVO;
import com.knowledge.common.dto.response.task.StageTriggerVO;
import com.knowledge.common.dto.response.task.StepLogVO;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.ThrowUtil;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.filecenter.service.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析控制面服务实现。
 *
 * @author cxxl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParseControlServiceImpl implements ParseControlService {

    private final KbFileResultDbService fileResultDbService;
    private final KbPipelineTaskDbService pipelineTaskDbService;
    private final KbPipelineProductDbService pipelineProductDbService;
    private final KbPipelineStepLogDbService stepLogDbService;
    private final TaskQueueSupport taskQueue;
    private final FileStorage fileStorage;

    /**
     * 触发解析（首次解析与失败重跑同一入口）四分支：
     * <ol>
     *   <li>RUNNING → 拒绝（40431，进行中勿重复触发）；</li>
     *   <li>QUEUED → 已登记未入队（登记与入队间崩溃的窗口），直接补投唤醒，不新建任务；</li>
     *   <li>SUCCESS/PARTIAL_SUCCESS → 拒绝（40437，成功后禁止重跑）；</li>
     *   <li>FAILED/CANCELLED/无任务 → 新建 PARSE 任务（QUEUED）落库并入队。</li>
     * </ol>
     * 旧任务/旧产物不动（重跑 = 新任务，历史可查）；完成后停在终态，不自动触发组装。
     *
     * @param fileResultId 文件结果 ID
     * @return 触发响应（新登记/已有任务 ID）
     */
    @Override
    public StageTriggerVO parse(Long fileResultId) {
        KbFileResult fileResult = fileResultDbService.getById(fileResultId);
        ThrowUtil.throwIf(ObjectUtil.isNull(fileResult), ErrorCode.FILE_RESULT_NOT_FOUND);
        // 手动逐环节口径：本接口 = "触发解析"（首次解析 / 失败后重跑同一个入口）
        KbPipelineTask existing = pipelineTaskDbService.getByFileResultIdAndStage(fileResultId,
                PipelineStage.PARSE.name());
        if (ObjectUtil.isNotNull(existing)) {
            ThrowUtil.throwIf(PipelineTaskStatus.RUNNING.name().equals(existing.getStatus()),
                    ErrorCode.TASK_ALREADY_PENDING, "解析任务进行中，请勿重复触发");
            if (PipelineTaskStatus.QUEUED.name().equals(existing.getStatus())) {
                // 已登记未入队（登记与入队间崩溃的窗口）：直接入队唤醒，不重复建任务
                taskQueue.enqueue(existing.getId());
                log.info("===> ParseControlServiceImpl parse 唤醒待解析任务, fileResultId={}, taskId={}",
                        fileResultId, existing.getId());
                return new StageTriggerVO(existing.getId());
            }
            // 成功/部分成功 = 禁止重跑（40437）
            ThrowUtil.throwIf(PipelineTaskStatus.SUCCESS.name().equals(existing.getStatus())
                    || PipelineTaskStatus.PARTIAL_SUCCESS.name().equals(existing.getStatus()),
                    ErrorCode.PARSE_ALREADY_SUCCEEDED);
        }
        KbPipelineTask task = new KbPipelineTask();
        task.setFileResultId(fileResultId);
        task.setStage(PipelineStage.PARSE.name());
        task.setStatus(PipelineTaskStatus.QUEUED.name());
        task.setRetryCount(0);
        pipelineTaskDbService.save(task);
        taskQueue.enqueue(task.getId());
        log.info("===> ParseControlServiceImpl parse 触发解析, fileResultId={}, taskId={}",
                fileResultId, task.getId());
        return new StageTriggerVO(task.getId());
    }

    @Override
    public ParseDetailVO parseDetail(Long fileResultId, Long taskId) {
        KbFileResult fileResult = fileResultDbService.getById(fileResultId);
        ThrowUtil.throwIf(ObjectUtil.isNull(fileResult), ErrorCode.FILE_RESULT_NOT_FOUND);
        KbPipelineTask task = ObjectUtil.isNull(taskId) ? null : pipelineTaskDbService.getById(taskId);
        if (ObjectUtil.isNull(task)) {
            task = pipelineTaskDbService.getByFileResultIdAndStage(fileResultId, PipelineStage.PARSE.name());
        }

        ParseDetailVO vo = new ParseDetailVO();
        vo.setFileResultId(fileResultId);
        if (ObjectUtil.isNotNull(task)) {
            vo.applyFrom(task);
            vo.setSteps(toStepVOs(task.getId()));
        }

        // 产物引用/告警：按 task.productId 精确取该次运行的产物（历史任务同样可展示自己的产物；无任务/无产物留空）
        vo.setWarnings(new ArrayList<>());
        KbPipelineProduct product = ObjectUtil.isNull(task) || task.getProductId() == null ? null
                : pipelineProductDbService.getById(task.getProductId());
        if (ObjectUtil.isNotNull(product)) {
            vo.setArtifactId(product.getArtifactId());
            vo.setContentHash(product.getContentHash());
            vo.setCapabilitySnapshot(product.getCapabilitySnapshot());
            vo.setWarnings(readWarnings(product.getArtifactId()));
        }
        return vo;
    }

    /** 子步骤实体 → VO（公共字段拷贝）。 */
    private List<StepLogVO> toStepVOs(Long taskId) {
        List<StepLogVO> vos = new ArrayList<>();
        for (KbPipelineStepLog step : stepLogDbService.listByTaskId(taskId)) {
            StepLogVO vo = new StepLogVO();
            vo.copyFrom(step);
            vos.add(vo);
        }
        return vos;
    }

    /** 读产物 JSON 提取 quality.warnings（展示用；读取失败记日志并返回空，不阻断详情） */
    private List<String> readWarnings(String artifactId) {
        try {
            byte[] content = fileStorage.getObject(artifactId);
            ParseResult result = JsonUtil.toObject(new String(content, StandardCharsets.UTF_8), ParseResult.class);
            if (ObjectUtil.isNull(result) || ObjectUtil.isNull(result.getQuality())) {
                return new ArrayList<>();
            }
            return result.getQuality().getWarnings().stream()
                    .map(w -> w.getLevel() + " " + w.getCode() + ": " + w.getMessage())
                    .toList();
        } catch (Exception e) {
            log.warn("读取产物告警失败, artifactId={}", artifactId, e);
            return new ArrayList<>();
        }
    }
}
