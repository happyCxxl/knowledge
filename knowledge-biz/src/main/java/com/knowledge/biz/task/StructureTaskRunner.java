package com.knowledge.biz.task;

import cn.hutool.core.util.StrUtil;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.parse.ParseResult;
import com.knowledge.common.domain.storage.ObjectRef;
import com.knowledge.common.domain.structure.AssembleOutcome;
import com.knowledge.common.domain.structure.UnifiedDocument;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.PipelineTaskErrorCode;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.common.utils.NullUtil;
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
        KbPipelineTask task = TaskRunnerSupport.claim(pipelineTaskDbService, taskId);
        if (NullUtil.isNull(task)) {
            return;
        }
        log.info("===> StructureTaskRunner 领取组装任务, taskId={}, fileResultId={}",
                taskId, task.getFileResultId());
        try {
            // 上游解析产物：优先任务指定值，缺省回退最新
            KbPipelineProduct parseProduct = resolveParseProduct(task);
            if (NullUtil.isNull(parseProduct)) {
                log.warn("===> StructureTaskRunner 组装失败：上游解析产物缺失, taskId={}, fileResultId={}",
                        taskId, task.getFileResultId());
                finishFailed(taskId, PipelineTaskErrorCode.STRUCTURE_UPSTREAM_UNREADABLE.name(),
                        "上游解析产物缺失，请先触发解析");
                return;
            }
            ParseResult parseResult;
            try {
                byte[] content = fileStorage.getObject(ObjectRef.ofProduct(parseProduct));
                parseResult = JsonUtil.toObject(new String(content, StandardCharsets.UTF_8), ParseResult.class);
            } catch (Exception e) {
                log.warn("读取上游解析产物失败, taskId={}, artifactId={}", taskId, parseProduct.getArtifactId(), e);
                finishFailed(taskId, PipelineTaskErrorCode.STRUCTURE_UPSTREAM_UNREADABLE.name(),
                        "上游解析产物读取失败: " + failureText(e));
                return;
            }
            if (NullUtil.isNull(parseResult)) {
                log.warn("===> StructureTaskRunner 组装失败：上游解析产物反序列化失败, taskId={}, artifactId={}",
                        taskId, parseProduct.getArtifactId());
                finishFailed(taskId, PipelineTaskErrorCode.STRUCTURE_UPSTREAM_UNREADABLE.name(),
                        "上游解析产物反序列化失败");
                return;
            }

            AssembleContext context = new AssembleContext();
            context.setTaskId(taskId);
            context.setFileResultId(task.getFileResultId());
            context.setProperties(structureProperties);
            AssembleOutcome outcome = documentAssembler.assemble(parseResult, context);

            TaskRunnerSupport.complete(pipelineTaskDbService, stepLogPersistence, taskId, outcome,
                    () -> persistProduct(task, parseProduct, outcome),
                    this::finishFailed);
        } catch (Exception e) {
            log.error("组装任务执行异常, taskId={}", taskId, e);
            finishFailed(taskId, errorCodeOf(e), failureText(e));
        }
    }

    /** 失败归口的任务错误码：上游文件/结果缺失按"上游不可读"归口，存储不一致走共享映射，其余按组装执行异常兜底 */
    private String errorCodeOf(Exception e) {
        if (e instanceof KnowledgeException knowledge && isUpstreamMissing(knowledge.getErrorCode())) {
            return PipelineTaskErrorCode.STRUCTURE_UPSTREAM_UNREADABLE.name();
        }
        return TaskRunnerSupport.failureCode(e, PipelineTaskErrorCode.STRUCTURE_FAILED);
    }

    /** 上游缺失判定：产物所在文件或文件结果已经不存在 */
    private boolean isUpstreamMissing(ErrorCode errorCode) {
        return errorCode == ErrorCode.FILE_NOT_FOUND || errorCode == ErrorCode.FILE_RESULT_NOT_FOUND;
    }

    /** 失败文案：业务异常用它自己的原因；其它异常带类名；消息为空时退化为类名，不落 "null" */
    private String failureText(Exception e) {
        String message = StrUtil.trimToNull(e.getMessage());
        if (e instanceof KnowledgeException) {
            return NullUtil.isNull(message) ? e.getClass().getSimpleName() : message;
        }
        return NullUtil.isNull(message) ? e.getClass().getSimpleName()
                : e.getClass().getSimpleName() + ": " + message;
    }

    /** 上游解析产物解析：任务指定 upstreamProductId 优先，查不到或缺省回退该环节最新产物。 */
    private KbPipelineProduct resolveParseProduct(KbPipelineTask task) {
        return TaskRunnerSupport.resolveUpstreamProduct(pipelineProductDbService, task, PipelineStage.PARSE);
    }

    private void persistProduct(KbPipelineTask task, KbPipelineProduct parseProduct, AssembleOutcome outcome) {
        UnifiedDocument document = Objects.requireNonNull(outcome.getDocument(), "组装结果为空");
        // 组装环节没有独立能力快照：产物行该列只承载 schemaVersion（解析环节的能力快照在 documentInfo 里）
        KbPipelineProduct product = productPersistence.persist(task, PipelineStage.STRUCTURE, parseProduct.getId(),
                JsonUtil.toJsonStr(Map.of("schemaVersion", UnifiedDocument.SCHEMA_VERSION)), document);
        stepLogPersistence.save(task.getId(), outcome.getStepLogs());
        log.info("===> StructureTaskRunner 组装完成, taskId={}, fileResultId={}, status={}, artifactId={}",
                task.getId(), task.getFileResultId(), outcome.getSuggestedStatus(), product.getArtifactId());
    }

    private void finishFailed(Long taskId, String errorCode, String errorMsg) {
        TaskRunnerSupport.finishFailed(pipelineTaskDbService, taskId, errorCode, errorMsg);
    }
}
