package com.knowledge.biz.task;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.preprocess.PreprocessOutcome;
import com.knowledge.common.domain.structure.UnifiedDocument;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.PipelineTaskErrorCode;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.filecenter.service.FileStorage;
import com.knowledge.worker.preprocessing.PreprocessContext;
import com.knowledge.worker.preprocessing.PreprocessProperties;
import com.knowledge.worker.preprocessing.PreprocessorPort;
import com.knowledge.worker.preprocessing.strategy.PreprocessStrategy;
import com.knowledge.worker.preprocessing.strategy.PreprocessStrategyParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 预处理任务执行器（消费循环调用）：领任务 → 读任务策略快照（触发时固定）→ 读上游 STRUCTURE 产物
 * （反序列化 UnifiedDocument）→ 调 worker 预处理管线 → 写产物存储 + 落库（product/9 条 step_log）→
 * 回写任务终态。原文 UnifiedDocument 只读零改动。
 *
 * @author cxxl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreprocessTaskRunner {

    private final KbPipelineTaskDbService pipelineTaskDbService;
    private final KbFileResultDbService fileResultDbService;
    private final KbPipelineProductDbService pipelineProductDbService;
    private final FileStorage fileStorage;
    private final PreprocessorPort preprocessor;
    private final PreprocessProperties preprocessProperties;
    private final PreprocessStrategyParser strategyParser;
    private final StepLogPersistence stepLogPersistence;
    private final ProductPersistence productPersistence;

    /**
     * 执行单个预处理任务（由消费循环提交，外层看门狗负责超时）。
     */
    public void run(Long taskId) {
        KbPipelineTask task = pipelineTaskDbService.getById(taskId);
        if (ObjectUtil.isNull(task)
                || !PipelineTaskStatus.QUEUED.name().equals(task.getStatus())) {
            return;
        }
        // 条件更新领任务：多实例防重复
        if (pipelineTaskDbService.claim(taskId) != 1) {
            return;
        }
        log.info("===> PreprocessTaskRunner 领取预处理任务, taskId={}, fileResultId={}",
                taskId, task.getFileResultId());
        try {
            KbFileResult fileResult = fileResultDbService.getById(task.getFileResultId());
            if (ObjectUtil.isNull(fileResult)) {
                finishFailed(taskId, PipelineTaskErrorCode.PREPROCESS_FAILED.name(),
                        "文件结果不存在: " + task.getFileResultId());
                return;
            }
            // 上游组装产物：优先任务指定值，缺省回退最新
            KbPipelineProduct structureProduct = resolveStructureProduct(task);
            if (ObjectUtil.isNull(structureProduct)) {
                finishFailed(taskId, PipelineTaskErrorCode.PREPROCESS_EMPTY.name(),
                        "统一结构产物不存在，请先触发组装");
                return;
            }

            PreprocessStrategy strategy = strategyParser.parse(task.getStrategySnapshot());
            UnifiedDocument document;
            try {
                byte[] content = fileStorage.getObject(structureProduct.getArtifactId());
                document = JsonUtil.toObject(new String(content, StandardCharsets.UTF_8), UnifiedDocument.class);
            } catch (Exception e) {
                log.warn("读取上游统一结构产物失败, taskId={}, artifactId={}", taskId, structureProduct.getArtifactId(), e);
                finishFailed(taskId, PipelineTaskErrorCode.PREPROCESS_EMPTY.name(),
                        "上游统一结构产物读取失败: " + truncate(String.valueOf(e.getMessage())));
                return;
            }
            if (ObjectUtil.isNull(document)) {
                log.warn("===> PreprocessTaskRunner 预处理失败：上游统一结构产物反序列化失败, taskId={}, artifactId={}",
                        taskId, structureProduct.getArtifactId());
                finishFailed(taskId, PipelineTaskErrorCode.PREPROCESS_EMPTY.name(), "上游统一结构产物反序列化失败");
                return;
            }

            PreprocessContext context = new PreprocessContext();
            context.setDocument(document);
            context.setStrategy(strategy);
            context.setProperties(preprocessProperties);
            context.setFileResultId(fileResult.getId());
            context.setUpstreamProductRef(structureProduct.getId());

            PreprocessOutcome outcome = preprocessor.preprocess(context);
            String suggested = outcome.getSuggestedStatus();
            if (PipelineTaskStatus.SUCCESS.name().equals(suggested)
                    || PipelineTaskStatus.PARTIAL_SUCCESS.name().equals(suggested)) {
                persistProduct(task, fileResult, structureProduct, strategy, outcome);
                // 手动逐环节口径：PREPROCESS 完成后停在终态，切片由页面手动触发
                pipelineTaskDbService.finish(taskId, suggested, null, null);
            } else {
                stepLogPersistence.save(taskId, outcome.getStepLogs());
                finishFailed(taskId, outcome.getErrorCode(), outcome.getErrorMsg());
            }
        } catch (Exception e) {
            log.error("预处理任务执行异常, taskId={}", taskId, e);
            finishFailed(taskId, PipelineTaskErrorCode.PREPROCESS_FAILED.name(), truncate(String.valueOf(e.getMessage())));
        }
    }

    /** 上游组装产物解析：任务指定 upstreamProductId 优先，查不到或缺省回退该环节最新产物。 */
    private KbPipelineProduct resolveStructureProduct(KbPipelineTask task) {
        KbPipelineProduct product = ObjectUtil.isNull(task.getUpstreamProductId()) ? null
                : pipelineProductDbService.getById(task.getUpstreamProductId());
        return ObjectUtil.isNull(product)
                ? pipelineProductDbService.getByFileResultIdAndStage(task.getFileResultId(), PipelineStage.STRUCTURE.name())
                : product;
    }

    /** 写产物存储 + 阶段产物引用 + 子步骤记录（成功/PARTIAL_SUCCESS 路径） */
    private void persistProduct(KbPipelineTask task, KbFileResult fileResult, KbPipelineProduct structureProduct,
                                PreprocessStrategy strategy, PreprocessOutcome outcome) {
        Objects.requireNonNull(outcome.getView(), "预处理结果为空");
        KbPipelineProduct product = productPersistence.persist(task, PipelineStage.PREPROCESS,
                structureProduct.getId(), JsonUtil.toJsonStr(strategy), outcome.getView());
        stepLogPersistence.save(task.getId(), outcome.getStepLogs());
        log.info("===> PreprocessTaskRunner 预处理完成, taskId={}, fileResultId={}, status={}, artifactId={}",
                task.getId(), fileResult.getId(), outcome.getSuggestedStatus(), product.getArtifactId());
    }

    private void finishFailed(Long taskId, String errorCode, String errorMsg) {
        log.warn("===> PreprocessTaskRunner 预处理任务失败, taskId={}, errorCode={}, errorMsg={}",
                taskId, errorCode, errorMsg);
        pipelineTaskDbService.finish(taskId, PipelineTaskStatus.FAILED.name(), errorCode,
                StrUtil.isBlank(errorMsg) ? null : truncate(errorMsg));
    }

    private String truncate(String message) {
        return StrUtil.maxLength(message, 1000);
    }
}
