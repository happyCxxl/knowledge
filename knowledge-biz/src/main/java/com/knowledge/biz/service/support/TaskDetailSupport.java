package com.knowledge.biz.service.support;

import cn.hutool.core.util.ObjectUtil;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineStepLogDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.dto.response.task.StageDetailVO;
import com.knowledge.common.dto.response.task.StepLogVO;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 环节详情共用助手：任务解析、详情骨架回填、该次运行的产物引用三件事各环节完全同构，
 * 集中在这里，各控制服务只留自己环节特有的产物装配。
 *
 * @author cxxl
 */
@Component
@RequiredArgsConstructor
public class TaskDetailSupport {

    private final KbPipelineTaskDbService pipelineTaskDbService;
    private final KbPipelineStepLogDbService stepLogDbService;
    private final KbPipelineProductDbService pipelineProductDbService;

    /**
     * 解析详情任务：taskId 指定则查该次运行并校验归属与环节（不匹配 40001）；缺省取该环节最新任务（可为空）。
     *
     * @param stageLabel 环节中文名（错误提示用）
     */
    public KbPipelineTask resolveTask(Long fileResultId, PipelineStage stage, Long taskId, String stageLabel) {
        if (ObjectUtil.isNull(taskId)) {
            return pipelineTaskDbService.getByFileResultIdAndStage(fileResultId, stage.name());
        }
        KbPipelineTask task = pipelineTaskDbService.getById(taskId);
        if (ObjectUtil.isNull(task)
                || !fileResultId.equals(task.getFileResultId())
                || !stage.name().equals(task.getStage())) {
            throw new KnowledgeException(ErrorCode.PARAM_INVALID, "任务不存在或不属于该文件的" + stageLabel + "任务");
        }
        return task;
    }

    /**
     * 详情骨架回填：文件结果 ID + 任务态字段 + 子步骤列表（无任务时只回填文件结果 ID，其余留空）。
     *
     * @param vo           环节详情 VO（各环节自己的子类）
     * @param fileResultId 文件结果 ID
     * @param task         该次运行任务（可为空）
     * @return 同一个 vo，便于链式书写
     */
    public <T extends StageDetailVO> T withTask(T vo, Long fileResultId, KbPipelineTask task) {
        vo.setFileResultId(fileResultId);
        if (ObjectUtil.isNotNull(task)) {
            vo.applyFrom(task);
            vo.setSteps(stepLogDbService.listByTaskId(task.getId()).stream().map(StepLogVO::of).toList());
        }
        return vo;
    }

    /**
     * 该次运行的产物：按 {@code task.productId} 精确取 —— 历史任务同样能展示自己那次运行的产物；
     * 无任务或无产物时返回 null（各环节据此留空）。
     */
    public KbPipelineProduct productOfTask(KbPipelineTask task) {
        return ObjectUtil.isNull(task) || task.getProductId() == null ? null
                : pipelineProductDbService.getById(task.getProductId());
    }

    /**
     * 产物公共三字段回填：产物 ID、内容哈希、能力快照（各环节详情共有的产物引用口径）。
     *
     * @param vo      环节详情 VO
     * @param product 该次运行的产物（非空）
     * @return 同一个 vo，便于链式书写
     */
    public <T extends StageDetailVO> T withProductRef(T vo, KbPipelineProduct product) {
        vo.setArtifactId(product.getArtifactId());
        vo.setContentHash(product.getContentHash());
        vo.setCapabilitySnapshot(product.getCapabilitySnapshot());
        return vo;
    }
}
