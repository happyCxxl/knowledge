package com.knowledge.biz.task;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.biz.service.db.KbChunkSetDbService;
import com.knowledge.biz.service.db.KbEmbeddingRecordDbService;
import com.knowledge.biz.service.db.KbEmbeddingSetDbService;
import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.common.domain.chunk.ChunkSet;
import com.knowledge.common.domain.embed.EmbedOutcome;
import com.knowledge.common.domain.embed.EmbeddingRecord;
import com.knowledge.common.domain.embed.EmbeddingSet;
import com.knowledge.common.domain.entity.KbChunkSet;
import com.knowledge.common.domain.entity.KbEmbeddingRecord;
import com.knowledge.common.domain.entity.KbEmbeddingSet;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.PipelineTaskErrorCode;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.enums.task.RowStatus;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.filecenter.service.FileStorage;
import com.knowledge.worker.chunking.ChunkProperties;
import com.knowledge.worker.chunking.strategy.ChunkStrategy;
import com.knowledge.worker.chunking.strategy.ChunkStrategyParser;
import com.knowledge.worker.embedding.EmbedContext;
import com.knowledge.worker.embedding.EmbedProperties;
import com.knowledge.worker.embedding.EmbedderPort;
import com.knowledge.worker.embedding.strategy.EmbedStrategy;
import com.knowledge.worker.embedding.strategy.EmbedStrategyParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 向量化任务执行器（消费循环调用）：领任务 → 读任务策略快照（触发时固定）→ 读最新 CHUNK 产物（ChunkSet）
 * → 加载复用候选账本（同文件同策略历史集合，新→旧，回溯上限内）→ 调 worker 向量化管线 →
 * 写产物存储 + 落库（product/kb_embedding_set/kb_embedding_record 分批/子步骤）→ 回写任务终态。
 *
 * @author cxxl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbedTaskRunner {

    private static final int BATCH_SIZE = 500;

    private final KbPipelineTaskDbService pipelineTaskDbService;
    private final KbFileResultDbService fileResultDbService;
    private final KbPipelineProductDbService pipelineProductDbService;
    private final KbChunkSetDbService chunkSetDbService;
    private final KbEmbeddingSetDbService embeddingSetDbService;
    private final KbEmbeddingRecordDbService embeddingRecordDbService;
    private final FileStorage fileStorage;
    private final EmbedderPort embedder;
    private final EmbedStrategyParser strategyParser;
    private final ChunkStrategyParser chunkStrategyParser;
    private final EmbedProperties embedProperties;
    private final ChunkProperties chunkProperties;
    private final StepLogPersistence stepLogPersistence;
    private final ProductPersistence productPersistence;

    /**
     * 执行单个向量化任务（由消费循环提交，外层看门狗负责超时）。
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
        log.info("===> EmbedTaskRunner 领取向量化任务, taskId={}, fileResultId={}",
                taskId, task.getFileResultId());
        try {
            KbFileResult fileResult = fileResultDbService.getById(task.getFileResultId());
            if (ObjectUtil.isNull(fileResult)) {
                finishFailed(taskId, PipelineTaskErrorCode.EMBED_FAILED.name(),
                        "文件结果不存在: " + task.getFileResultId());
                return;
            }
            // 上游切片产物：优先任务指定值，缺省回退最新
            KbPipelineProduct chunkProduct = resolveChunkProduct(task);
            if (ObjectUtil.isNull(chunkProduct)) {
                finishFailed(taskId, PipelineTaskErrorCode.EMBED_EMPTY.name(), "切片产物不存在，请先触发切片");
                return;
            }

            EmbedStrategy strategy = strategyParser.parse(task.getStrategySnapshot());
            ChunkSet chunkSet;
            try {
                byte[] content = fileStorage.getObject(chunkProduct.getArtifactId());
                chunkSet = JsonUtil.toObject(new String(content, StandardCharsets.UTF_8), ChunkSet.class);
            } catch (Exception e) {
                log.warn("读取上游切片产物失败, taskId={}, artifactId={}", taskId, chunkProduct.getArtifactId(), e);
                finishFailed(taskId, PipelineTaskErrorCode.EMBED_EMPTY.name(),
                        "上游切片产物读取失败: " + truncate(String.valueOf(e.getMessage())));
                return;
            }
            if (ObjectUtil.isNull(chunkSet) || chunkSet.getChunks().isEmpty()) {
                finishFailed(taskId, PipelineTaskErrorCode.EMBED_EMPTY.name(), "上游切片产物为空");
                return;
            }

            EmbedContext context = new EmbedContext();
            context.setChunkSet(chunkSet);
            context.setChunkSetRef(latestChunkSetRef(fileResult.getId()));
            context.setChunkStrategy(resolveChunkStrategy(chunkProduct.getCapabilitySnapshot()));
            context.setChunkProperties(chunkProperties);
            context.setStrategy(strategy);
            context.setProperties(embedProperties);
            context.setFileResultId(fileResult.getId());
            context.setReuseCandidates(loadReuseCandidates(fileResult.getId(), strategy, task.getId()));

            EmbedOutcome outcome = embedder.embed(context);
            TaskRunnerSupport.complete(pipelineTaskDbService, stepLogPersistence, taskId, outcome,
                    () -> persistProduct(task, fileResult, chunkProduct, strategy, outcome),
                    this::finishFailed);
        } catch (Exception e) {
            log.error("向量化任务执行异常, taskId={}", taskId, e);
            finishFailed(taskId, PipelineTaskErrorCode.EMBED_FAILED.name(), truncate(String.valueOf(e.getMessage())));
        }
    }

    /** 上游切片产物解析：任务指定 upstreamProductId 优先，查不到或缺省回退该环节最新产物。 */
    private KbPipelineProduct resolveChunkProduct(KbPipelineTask task) {
        KbPipelineProduct product = ObjectUtil.isNull(task.getUpstreamProductId()) ? null
                : pipelineProductDbService.getById(task.getUpstreamProductId());
        return ObjectUtil.isNull(product)
                ? pipelineProductDbService.getByFileResultIdAndStage(task.getFileResultId(), PipelineStage.CHUNK.name())
                : product;
    }

    /** 复用候选账本：同文件同策略成功集合（新→旧，回溯上限）；账本文件读取失败跳过继续（复用是优化） */
    private List<EmbeddingSet> loadReuseCandidates(Long fileResultId, EmbedStrategy strategy, Long taskId) {
        if (!strategy.cacheOn()) {
            return List.of();
        }
        List<EmbeddingSet> candidates = new ArrayList<>();
        List<KbEmbeddingSet> rows = embeddingSetDbService.listHistoryByFileResultIdAndStrategyVersion(
                fileResultId, strategy.fullVersion(), embedProperties.getReuseBacktrackLimit());
        for (KbEmbeddingSet row : rows) {
            try {
                byte[] content = fileStorage.getObject(row.getArtifactId());
                EmbeddingSet set = JsonUtil.toObject(new String(content, StandardCharsets.UTF_8), EmbeddingSet.class);
                if (set != null) {
                    candidates.add(set);
                }
            } catch (Exception e) {
                log.warn("复用候选账本读取失败，跳过回溯, taskId={}, embeddingSetId={}, artifactId={}",
                        taskId, row.getEmbeddingSetId(), row.getArtifactId(), e);
            }
        }
        return candidates;
    }

    private Long latestChunkSetRef(Long fileResultId) {
        KbChunkSet chunkSet = chunkSetDbService.getLatestByFileResultId(fileResultId);
        return ObjectUtil.isNull(chunkSet) ? null : chunkSet.getId();
    }

    private ChunkStrategy resolveChunkStrategy(String snapshot) {
        if (StrUtil.isBlank(snapshot)) {
            return null;
        }
        return chunkStrategyParser.parse(snapshot);
    }

    /** 写产物存储 + product/kb_embedding_set/kb_embedding_record + 子步骤记录（成功/PARTIAL_SUCCESS 路径） */
    private void persistProduct(KbPipelineTask task, KbFileResult fileResult, KbPipelineProduct chunkProduct,
                                EmbedStrategy strategy, EmbedOutcome outcome) {
        EmbeddingSet embeddingSet = Objects.requireNonNull(outcome.getEmbeddingSet(), "向量化结果为空");
        KbPipelineProduct product = productPersistence.persist(task, PipelineStage.EMBED,
                chunkProduct.getId(), JsonUtil.toJsonStr(strategy), embeddingSet);

        KbEmbeddingSet setRow = new KbEmbeddingSet();
        setRow.setFileResultId(fileResult.getId());
        setRow.setChunkSetRef(embeddingSet.getChunkSetRef());
        setRow.setEmbeddingSetId(embeddingSet.getEmbeddingSetId());
        setRow.setStrategyVersion(embeddingSet.getStrategyVersion());
        setRow.setModel(embeddingSet.getModel());
        setRow.setDimension(embeddingSet.getDimension());
        setRow.setMetric(embeddingSet.getMetric());
        setRow.setNormalized(embeddingSet.isNormalized());
        setRow.setRecordCount(embeddingSet.getRecordCount());
        setRow.setCachedCount(embeddingSet.getCachedCount());
        setRow.setStatus(RowStatus.ACTIVE.name());
        setRow.setArtifactId(product.getArtifactId());
        embeddingSetDbService.save(setRow);

        List<KbEmbeddingRecord> recordRows = new ArrayList<>();
        for (EmbeddingRecord record : embeddingSet.getRecords()) {
            recordRows.add(toKbRecord(setRow.getId(), record));
        }
        embeddingRecordDbService.saveBatch(recordRows, BATCH_SIZE);

        stepLogPersistence.save(task.getId(), outcome.getStepLogs());
        log.info("===> EmbedTaskRunner 向量化完成, taskId={}, fileResultId={}, status={}, recordCount={}, cachedCount={}, artifactId={}",
                task.getId(), fileResult.getId(), outcome.getSuggestedStatus(),
                embeddingSet.getRecordCount(), embeddingSet.getCachedCount(), product.getArtifactId());
    }

    private KbEmbeddingRecord toKbRecord(Long embeddingSetId, EmbeddingRecord record) {
        KbEmbeddingRecord row = new KbEmbeddingRecord();
        row.setEmbeddingSetId(embeddingSetId);
        row.setEmbeddingId(record.getEmbeddingId());
        row.setChunkId(record.getChunkId());
        row.setContentType(record.getContentType());
        row.setParentChunkId(record.getParentChunkId());
        row.setInputText(record.getInputText());
        row.setInputTextHash(record.getInputTextHash());
        row.setTokenCount(record.getTokenCount());
        row.setRequestId(record.getRequestId());
        row.setStatus(record.getStatus());
        row.setCacheHit(record.isCacheHit());
        return row;
    }

    private void finishFailed(Long taskId, String errorCode, String errorMsg) {
        log.warn("===> EmbedTaskRunner 向量化任务失败, taskId={}, errorCode={}, errorMsg={}",
                taskId, errorCode, errorMsg);
        pipelineTaskDbService.finish(taskId, PipelineTaskStatus.FAILED.name(), errorCode,
                StrUtil.isBlank(errorMsg) ? null : truncate(errorMsg));
    }

    private String truncate(String message) {
        return StrUtil.maxLength(message, 1000);
    }
}
