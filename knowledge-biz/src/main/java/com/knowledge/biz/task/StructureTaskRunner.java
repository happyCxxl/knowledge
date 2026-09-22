package com.knowledge.biz.task;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.parse.ParseResult;
import com.knowledge.common.domain.structure.AssembleOutcome;
import com.knowledge.common.domain.structure.UnifiedDocument;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.PipelineTaskErrorCode;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.filecenter.service.FileStorage;
import com.knowledge.worker.structure.AssembleContext;
import com.knowledge.worker.structure.DocumentAssemblerPort;
import com.knowledge.worker.structure.StructureProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * 统一组装任务执行器（STRUCTURE 环节）：领任务 → 读上游 ParseResult 产物 →
 * 调 worker 组装管线 → 写 UnifiedDocument 产物 + STRUCTURE 产物行（upstream 链）→
 * 子步骤记录 → 回写终态（空树 FAILED / 其余 SUCCESS）。
 *
 * @author cxxl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StructureTaskRunner {

    private final KbPipelineTaskDbService pipelineTaskDbService;
    private final KbPipelineProductDbService pipelineProductDbService;
    private final FileStorage fileStorage;
    private final DocumentAssemblerPort documentAssembler;
    private final StructureProperties structureProperties;
    private final StepLogPersistence stepLogPersistence;
    private final ProductPersistence productPersistence;

    /**
     * 执行单个组装任务（由消费循环提交，外层看门狗负责超时）。
     */
    public void run(Long taskId) {
        KbPipelineTask task = pipelineTaskDbService.getById(taskId);
        if (ObjectUtil.isNull(task)
                || !PipelineTaskStatus.QUEUED.name().equals(task.getStatus())) {
            return;
        }
        if (pipelineTaskDbService.claim(taskId) != 1) {
            return;
        }
        log.info("===> StructureTaskRunner 领取组装任务, taskId={}, fileResultId={}",
                taskId, task.getFileResultId());
        try {
            // 上游解析产物：优先任务指定值，缺省回退最新
            KbPipelineProduct parseProduct = resolveParseProduct(task);
            if (ObjectUtil.isNull(parseProduct)) {
                log.warn("===> StructureTaskRunner 组装失败：上游解析产物缺失, taskId={}, fileResultId={}",
                        taskId, task.getFileResultId());
                finishFailed(taskId, PipelineTaskErrorCode.STRUCTURE_EMPTY.name(), "上游解析产物缺失，请先触发解析");
                return;
            }
            ParseResult parseResult;
            try {
                byte[] content = fileStorage.getObject(parseProduct.getArtifactId());
                parseResult = JsonUtil.toObject(new String(content, StandardCharsets.UTF_8), ParseResult.class);
            } catch (Exception e) {
                log.warn("读取上游解析产物失败, taskId={}, artifactId={}", taskId, parseProduct.getArtifactId(), e);
                finishFailed(taskId, PipelineTaskErrorCode.STRUCTURE_EMPTY.name(),
                        "上游解析产物读取失败: " + truncate(String.valueOf(e.getMessage())));
                return;
            }
            if (ObjectUtil.isNull(parseResult)) {
                log.warn("===> StructureTaskRunner 组装失败：上游解析产物反序列化失败, taskId={}, artifactId={}",
                        taskId, parseProduct.getArtifactId());
                finishFailed(taskId, PipelineTaskErrorCode.STRUCTURE_EMPTY.name(), "上游解析产物反序列化失败");
                return;
            }

            AssembleContext context = new AssembleContext();
            context.setTaskId(taskId);
            context.setFileResultId(task.getFileResultId());
            context.setProperties(structureProperties);
            AssembleOutcome outcome = documentAssembler.assemble(parseResult, context);

            String suggested = outcome.getSuggestedStatus();
            if (PipelineTaskStatus.SUCCESS.name().equals(suggested)
                    || PipelineTaskStatus.PARTIAL_SUCCESS.name().equals(suggested)) {
                persistProduct(task, parseProduct, outcome);
                pipelineTaskDbService.finish(taskId, suggested, null, null);
            } else {
                stepLogPersistence.save(taskId, outcome.getStepLogs());
                finishFailed(taskId, outcome.getErrorCode(), outcome.getErrorMsg());
            }
        } catch (Exception e) {
            log.error("组装任务执行异常, taskId={}", taskId, e);
            finishFailed(taskId, PipelineTaskErrorCode.STRUCTURE_FAILED.name(), truncate(String.valueOf(e.getMessage())));
        }
    }

    /** 上游解析产物解析：任务指定 upstreamProductId 优先，查不到或缺省回退该环节最新产物。 */
    private KbPipelineProduct resolveParseProduct(KbPipelineTask task) {
        KbPipelineProduct product = ObjectUtil.isNull(task.getUpstreamProductId()) ? null
                : pipelineProductDbService.getById(task.getUpstreamProductId());
        return ObjectUtil.isNull(product)
                ? pipelineProductDbService.getByFileResultIdAndStage(task.getFileResultId(), PipelineStage.PARSE.name())
                : product;
    }

    private void persistProduct(KbPipelineTask task, KbPipelineProduct parseProduct, AssembleOutcome outcome) {
        UnifiedDocument document = Objects.requireNonNull(outcome.getDocument(), "组装结果为空");
        KbPipelineProduct product = productPersistence.persist(task, PipelineStage.STRUCTURE, parseProduct.getId(),
                JsonUtil.toJsonStr(Map.of("schemaVersion", UnifiedDocument.SCHEMA_VERSION)), document);
        stepLogPersistence.save(task.getId(), outcome.getStepLogs());
        log.info("===> StructureTaskRunner 组装完成, taskId={}, fileResultId={}, status={}, artifactId={}",
                task.getId(), task.getFileResultId(), outcome.getSuggestedStatus(), product.getArtifactId());
    }

    private void finishFailed(Long taskId, String errorCode, String errorMsg) {
        pipelineTaskDbService.finish(taskId, PipelineTaskStatus.FAILED.name(), errorCode,
                StrUtil.isBlank(errorMsg) ? null : truncate(errorMsg));
    }

    private String truncate(String message) {
        return StrUtil.maxLength(message, 1000);
    }
}
