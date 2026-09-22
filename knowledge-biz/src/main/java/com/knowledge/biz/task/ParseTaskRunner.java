package com.knowledge.biz.task;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.biz.service.db.KbSourceFileDbService;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.entity.KbSourceFile;
import com.knowledge.common.domain.input.FileReference;
import com.knowledge.common.domain.parse.ParseOutcome;
import com.knowledge.common.domain.parse.ParseResult;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.PipelineTaskErrorCode;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.enums.task.RowStatus;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.filecenter.service.FileStorage;
import com.knowledge.worker.parser.ParseContext;
import com.knowledge.worker.parser.ParseProperties;
import com.knowledge.worker.parser.impl.ParsePipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 解析任务执行器（消费循环调用）：领任务 → 读文档输入环节建档数据 → 开流调 worker 解析管线 →
 * 写产物存储 + 落库（product/step_log）→ 回写任务终态。
 * 状态回写只更新 kb_pipeline_task 自身与产物引用，不回写领域规则。
 *
 * @author cxxl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParseTaskRunner {

    private final KbPipelineTaskDbService pipelineTaskDbService;
    private final KbFileResultDbService fileResultDbService;
    private final KbSourceFileDbService sourceFileDbService;
    private final KbPipelineProductDbService pipelineProductDbService;
    private final FileStorage fileStorage;
    private final ParsePipeline parsePipeline;
    private final ParseProperties parseProperties;
    private final StepLogPersistence stepLogPersistence;

    /**
     * 执行单个解析任务（由消费循环提交，外层看门狗负责超时）。
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
        try {
            KbFileResult fileResult = fileResultDbService.getById(task.getFileResultId());
            if (ObjectUtil.isNull(fileResult)) {
                finishFailed(taskId, PipelineTaskErrorCode.PARSE_FAILED.name(),
                        "文件结果不存在: " + task.getFileResultId());
                return;
            }
            KbSourceFile sourceFile = sourceFileDbService.getById(fileResult.getSourceFileId());
            if (ObjectUtil.isNull(sourceFile)) {
                finishFailed(taskId, PipelineTaskErrorCode.PARSE_FAILED.name(),
                        "来源文件不存在: " + fileResult.getSourceFileId());
                return;
            }
            log.info("===> ParseTaskRunner 开始解析, taskId={}, fileResultId={}, fileId={}, mimeType={}",
                    taskId, fileResult.getId(), sourceFile.getFileId(), sourceFile.getMimeType());

            ParseContext context = new ParseContext();
            context.setFileResultId(fileResult.getId());
            context.setFileRef(toFileRef(sourceFile));
            context.setProperties(parseProperties);

            ParseOutcome outcome;
            try (InputStream in = fileStorage.open(sourceFile.getFileId())) {
                context.setInputStream(in);
                outcome = parsePipeline.run(context);
            } catch (KnowledgeException e) {
                if (e.getErrorCode() == ErrorCode.FILE_NOT_FOUND) {
                    finishFailed(taskId, PipelineTaskErrorCode.PARSE_FAILED.name(),
                            "文件不存在或不可读（提交后可能被删除）");
                    return;
                }
                throw e;
            }

            String suggested = outcome.getSuggestedStatus();
            if (PipelineTaskStatus.SUCCESS.name().equals(suggested)
                    || PipelineTaskStatus.PARTIAL_SUCCESS.name().equals(suggested)) {
                persistProduct(task, fileResult, outcome);
                // 手动逐环节口径：PARSE 完成后停在终态，统一结构由页面手动触发
                pipelineTaskDbService.finish(taskId, suggested, null, null);
            } else {
                stepLogPersistence.save(taskId, outcome.getStepLogs());
                finishFailed(taskId, outcome.getErrorCode(), outcome.getErrorMsg());
            }
        } catch (Exception e) {
            log.error("解析任务执行异常, taskId={}", taskId, e);
            finishFailed(taskId, PipelineTaskErrorCode.PARSE_FAILED.name(), truncate(String.valueOf(e.getMessage())));
        }
    }

    /** 写产物存储 + 阶段产物引用 + 子步骤记录（成功/PARTIAL_SUCCESS 路径）；返回 PARSE 产物行 */
    private KbPipelineProduct persistProduct(KbPipelineTask task, KbFileResult fileResult, ParseOutcome outcome) {
        ParseResult parseResult = Objects.requireNonNull(outcome.getParseResult(), "解析结果为空");
        String json = JsonUtil.toJsonStr(parseResult);
        byte[] content = json.getBytes(StandardCharsets.UTF_8);
        String artifactId = fileStorage.putObject(content);

        KbPipelineProduct product = new KbPipelineProduct();
        product.setFileResultId(fileResult.getId());
        product.setStage(PipelineStage.PARSE.name());
        product.setCapabilitySnapshot(JsonUtil.toJsonStr(parseResult.getCapabilitySnapshot()));
        product.setArtifactId(artifactId);
        product.setContentHash(artifactId);
        product.setStatus(RowStatus.ACTIVE.name());
        pipelineProductDbService.save(product);
        pipelineTaskDbService.updateProductId(task.getId(), product.getId());

        stepLogPersistence.save(task.getId(), outcome.getStepLogs());
        log.info("===> ParseTaskRunner 解析完成, taskId={}, fileResultId={}, status={}, artifactId={}",
                task.getId(), fileResult.getId(), outcome.getSuggestedStatus(), artifactId);
        return product;
    }

    /** 失败终态回写：统一留痕（带 taskId + 错误码，便于按任务关联排查）。 */
    private void finishFailed(Long taskId, String errorCode, String errorMsg) {
        log.warn("===> ParseTaskRunner 解析任务失败, taskId={}, errorCode={}, errorMsg={}",
                taskId, errorCode, errorMsg);
        pipelineTaskDbService.finish(taskId, PipelineTaskStatus.FAILED.name(), errorCode,
                StrUtil.isBlank(errorMsg) ? null : truncate(errorMsg));
    }

    private FileReference toFileRef(KbSourceFile sourceFile) {
        FileReference fileRef = new FileReference();
        fileRef.setFileId(sourceFile.getFileId());
        fileRef.setFileName(sourceFile.getFileName());
        fileRef.setSha256(sourceFile.getSha256());
        fileRef.setMimeType(sourceFile.getMimeType());
        return fileRef;
    }

    private String truncate(String message) {
        return StrUtil.maxLength(message, 1000);
    }
}
