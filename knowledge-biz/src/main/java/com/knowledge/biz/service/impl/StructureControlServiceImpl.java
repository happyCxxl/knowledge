package com.knowledge.biz.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.knowledge.biz.service.StructureControlService;
import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineStepLogDbService;
import com.knowledge.biz.service.support.StructureVoAssembler;
import com.knowledge.biz.service.support.TaskDetailSupport;
import com.knowledge.biz.task.TaskTriggerSupport;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.structure.UnifiedDocument;
import com.knowledge.common.dto.response.structure.StructureDetailVO;
import com.knowledge.common.dto.response.task.StageTriggerVO;
import com.knowledge.common.dto.response.task.StepLogVO;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.ThrowUtil;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.filecenter.service.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * 结构组装控制面服务实现（手动逐环节）：
 * 上游产物校验 → 防重/唤醒（RUNNING 40431；QUEUED 补投唤醒，与其余环节口径一致）→
 * 新建 STRUCTURE 任务（upstream=最新 PARSE 产物）入队；
 * 组装详情：任务状态 + 子步骤 + 统一文档产物推导的统计/告警/冲突/文档内容大纲。
 *
 * @author cxxl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StructureControlServiceImpl implements StructureControlService {

    private final KbFileResultDbService fileResultDbService;
    private final KbPipelineProductDbService pipelineProductDbService;
    private final KbPipelineStepLogDbService stepLogDbService;
    private final TaskTriggerSupport triggerSupport;
    private final TaskDetailSupport detailSupport;
    private final FileStorage fileStorage;
    private final StructureVoAssembler voAssembler;

    /**
     * 触发组装（手动逐环节，重跑同入口）：上游解析产物校验 → 防重/唤醒 → 新建 STRUCTURE 任务入队。
     *
     * @param fileResultId      文件结果 ID
     * @param upstreamProductId 上游解析产物 ID（可选，缺省取最新）
     * @return 触发响应（新登记/已有任务 ID）
     */
    @Override
    public StageTriggerVO structure(Long fileResultId, Long upstreamProductId) {
        KbFileResult fileResult = fileResultDbService.getById(fileResultId);
        ThrowUtil.throwIf(ObjectUtil.isNull(fileResult), ErrorCode.FILE_RESULT_NOT_FOUND);
        // 可选指定上游解析产物；缺省取最新
        KbPipelineProduct parseProduct = requireParseProduct(fileResultId, upstreamProductId);
        ThrowUtil.throwIf(ObjectUtil.isNull(parseProduct), ErrorCode.FILE_RESULT_NOT_FOUND,
                "解析产物不存在，请先触发解析");
        return triggerSupport.trigger(fileResultId, PipelineStage.STRUCTURE, parseProduct.getId(), "组装", false);
    }

    @Override
    public StructureDetailVO structureDetail(Long fileResultId, Long taskId) {
        KbFileResult fileResult = fileResultDbService.getById(fileResultId);
        ThrowUtil.throwIf(ObjectUtil.isNull(fileResult), ErrorCode.FILE_RESULT_NOT_FOUND);
        KbPipelineTask task = detailSupport.resolveTask(fileResultId, PipelineStage.STRUCTURE, taskId, "组装");

        StructureDetailVO vo = new StructureDetailVO();
        vo.setFileResultId(fileResultId);
        if (ObjectUtil.isNotNull(task)) {
            vo.applyFrom(task);
            vo.setSteps(stepLogDbService.listByTaskId(task.getId()).stream().map(StepLogVO::of).toList());
        }

        // 产物引用/统计/冲突/文档内容：按 task.productId 精确取该次运行的产物（历史任务同样可展示自己的产物；无任务/无产物留空）
        vo.setWarnings(new ArrayList<>());
        vo.setConflicts(new ArrayList<>());
        vo.setOutline(new ArrayList<>());
        KbPipelineProduct product = ObjectUtil.isNull(task) || task.getProductId() == null ? null
                : pipelineProductDbService.getById(task.getProductId());
        if (ObjectUtil.isNotNull(product)) {
            vo.setArtifactId(product.getArtifactId());
            vo.setContentHash(product.getContentHash());
            vo.setCapabilitySnapshot(product.getCapabilitySnapshot());
            readDocument(product.getArtifactId(), vo);
        }
        return vo;
    }

    /** 上游解析产物校验：指定 id 则校验存在/环节/归属；缺省取该文件结果最新 PARSE 产物。 */
    private KbPipelineProduct requireParseProduct(Long fileResultId, Long productId) {
        if (ObjectUtil.isNull(productId)) {
            return pipelineProductDbService.getByFileResultIdAndStage(fileResultId, PipelineStage.PARSE.name());
        }
        KbPipelineProduct product = pipelineProductDbService.getById(productId);
        ThrowUtil.throwIf(ObjectUtil.isNull(product), ErrorCode.FILE_RESULT_NOT_FOUND, "指定上游产物不存在");
        ThrowUtil.throwIf(!PipelineStage.PARSE.name().equals(product.getStage()), ErrorCode.FILE_RESULT_NOT_FOUND,
                "指定产物环节不匹配：期望 PARSE");
        ThrowUtil.throwIf(!fileResultId.equals(product.getFileResultId()), ErrorCode.FILE_RESULT_NOT_FOUND,
                "指定产物不属于该文件结果");
        return product;
    }

    /** 读统一文档产物并委托 VO 组装器提取统计/告警/冲突/文档内容大纲（读取失败记日志并留空，不阻断详情） */
    private void readDocument(String artifactId, StructureDetailVO vo) {
        try {
            byte[] content = fileStorage.getObject(artifactId);
            UnifiedDocument document = JsonUtil.toObject(
                    new String(content, StandardCharsets.UTF_8), UnifiedDocument.class);
            if (ObjectUtil.isNull(document)) {
                return;
            }
            vo.setSummary(voAssembler.toSummary(document));
            vo.setWarnings(voAssembler.toWarningTexts(document.getQuality()));
            vo.setConflicts(voAssembler.toConflicts(document.getQuality()));
            vo.setOutline(voAssembler.toOutline(document));
        } catch (Exception e) {
            log.warn("读取统一文档产物失败, artifactId={}", artifactId, e);
        }
    }
}
