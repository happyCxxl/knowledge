package com.knowledge.biz.task;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.biz.service.db.KbChunkDbService;
import com.knowledge.biz.service.db.KbChunkSetDbService;
import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.common.domain.chunk.Chunk;
import com.knowledge.common.domain.chunk.ChunkOutcome;
import com.knowledge.common.domain.chunk.ChunkSet;
import com.knowledge.common.domain.entity.KbChunk;
import com.knowledge.common.domain.entity.KbChunkSet;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.preprocess.PreprocessView;
import com.knowledge.common.domain.rules.ChunkRules;
import com.knowledge.common.domain.structure.UnifiedDocument;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.PipelineTaskErrorCode;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.enums.task.RowStatus;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.filecenter.service.FileStorage;
import com.knowledge.worker.chunking.ChunkContext;
import com.knowledge.worker.chunking.ChunkProperties;
import com.knowledge.worker.chunking.ChunkerPort;
import com.knowledge.worker.chunking.strategy.ChunkStrategy;
import com.knowledge.worker.chunking.strategy.ChunkStrategyParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 切片任务执行器（消费循环调用）：领任务 → 读任务策略快照（触发时固定）→ 读最新 PREPROCESS 视图产物
 * （+ 其上游 STRUCTURE 统一文档作结构参照，缺失不阻断、titlePath/图注降级）→ 调 worker 切片管线 →
 * 写产物存储 + 落库（product/kb_chunk_set/kb_chunk 分批/6 条 step_log）→ 回写任务终态。
 *
 * @author cxxl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChunkTaskRunner {

    private static final int BATCH_SIZE = 500;

    private final KbPipelineTaskDbService pipelineTaskDbService;
    private final KbFileResultDbService fileResultDbService;
    private final KbPipelineProductDbService pipelineProductDbService;
    private final KbChunkSetDbService chunkSetDbService;
    private final KbChunkDbService chunkDbService;
    private final FileStorage fileStorage;
    private final ChunkerPort chunker;
    private final ChunkProperties chunkProperties;
    private final ChunkStrategyParser strategyParser;
    private final StepLogPersistence stepLogPersistence;
    private final ProductPersistence productPersistence;

    /**
     * 执行单个切片任务（由消费循环提交，外层看门狗负责超时）。
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
        log.info("===> ChunkTaskRunner 领取切片任务, taskId={}, fileResultId={}",
                taskId, task.getFileResultId());
        try {
            KbFileResult fileResult = fileResultDbService.getById(task.getFileResultId());
            if (ObjectUtil.isNull(fileResult)) {
                finishFailed(taskId, PipelineTaskErrorCode.CHUNK_FAILED.name(),
                        "文件结果不存在: " + task.getFileResultId());
                return;
            }
            // 上游预处理视图产物：优先任务指定值，缺省回退最新
            KbPipelineProduct preprocessProduct = resolvePreprocessProduct(task);
            if (ObjectUtil.isNull(preprocessProduct)) {
                finishFailed(taskId, PipelineTaskErrorCode.CHUNK_EMPTY.name(),
                        ChunkRules.UPSTREAM_PREPROCESS_MISSING);
                return;
            }

            ChunkStrategy strategy = strategyParser.parse(task.getStrategySnapshot());
            PreprocessView view;
            try {
                byte[] content = fileStorage.getObject(preprocessProduct.getArtifactId());
                view = JsonUtil.toObject(new String(content, StandardCharsets.UTF_8), PreprocessView.class);
            } catch (Exception e) {
                log.warn("读取上游预处理视图产物失败, taskId={}, artifactId={}", taskId, preprocessProduct.getArtifactId(), e);
                finishFailed(taskId, PipelineTaskErrorCode.CHUNK_EMPTY.name(),
                        "上游预处理视图产物读取失败: " + truncate(String.valueOf(e.getMessage())));
                return;
            }
            if (ObjectUtil.isNull(view)) {
                log.warn("===> ChunkTaskRunner 切片失败：上游预处理视图产物反序列化失败, taskId={}, artifactId={}",
                        taskId, preprocessProduct.getArtifactId());
                finishFailed(taskId, PipelineTaskErrorCode.CHUNK_EMPTY.name(), "上游预处理视图产物反序列化失败");
                return;
            }
            UnifiedDocument document = readStructureDocument(preprocessProduct);

            ChunkContext context = new ChunkContext();
            context.setView(view);
            context.setDocument(document);
            context.setStrategy(strategy);
            context.setProperties(chunkProperties);
            context.setFileResultId(fileResult.getId());
            context.setUpstreamProductRef(preprocessProduct.getId());

            ChunkOutcome outcome = chunker.chunk(context);
            TaskRunnerSupport.complete(pipelineTaskDbService, stepLogPersistence, taskId, outcome,
                    () -> persistProduct(task, fileResult, preprocessProduct, strategy, outcome),
                    this::finishFailed);
        } catch (Exception e) {
            log.error("切片任务执行异常, taskId={}", taskId, e);
            finishFailed(taskId, PipelineTaskErrorCode.CHUNK_FAILED.name(), truncate(String.valueOf(e.getMessage())));
        }
    }

    /** 上游预处理产物解析：任务指定 upstreamProductId 优先，查不到或缺省回退该环节最新产物。 */
    private KbPipelineProduct resolvePreprocessProduct(KbPipelineTask task) {
        KbPipelineProduct product = ObjectUtil.isNull(task.getUpstreamProductId()) ? null
                : pipelineProductDbService.getById(task.getUpstreamProductId());
        return ObjectUtil.isNull(product)
                ? pipelineProductDbService.getByFileResultIdAndStage(task.getFileResultId(), PipelineStage.PREPROCESS.name())
                : product;
    }

    /** 结构参照：PREPROCESS 产物上游的 STRUCTURE 产物（缺失不阻断，titlePath/图注降级） */
    private UnifiedDocument readStructureDocument(KbPipelineProduct preprocessProduct) {
        if (ObjectUtil.isNull(preprocessProduct.getUpstreamProductId())) {
            return null;
        }
        try {
            KbPipelineProduct structureProduct = pipelineProductDbService
                    .getById(preprocessProduct.getUpstreamProductId());
            if (ObjectUtil.isNull(structureProduct)) {
                return null;
            }
            byte[] content = fileStorage.getObject(structureProduct.getArtifactId());
            return JsonUtil.toObject(new String(content, StandardCharsets.UTF_8), UnifiedDocument.class);
        } catch (Exception e) {
            log.warn("读取结构参照产物失败（titlePath/图注降级）, fileResultId={}",
                    preprocessProduct.getFileResultId(), e);
            return null;
        }
    }

    /** 写产物存储 + product/kb_chunk_set/kb_chunk + 子步骤记录（成功/PARTIAL_SUCCESS 路径） */
    private void persistProduct(KbPipelineTask task, KbFileResult fileResult, KbPipelineProduct preprocessProduct,
                                ChunkStrategy strategy, ChunkOutcome outcome) {
        ChunkSet chunkSet = Objects.requireNonNull(outcome.getChunkSet(), "切片结果为空");
        KbPipelineProduct product = productPersistence.persist(task, PipelineStage.CHUNK,
                preprocessProduct.getId(), JsonUtil.toJsonStr(strategy), chunkSet);

        KbChunkSet chunkSetRow = new KbChunkSet();
        chunkSetRow.setFileResultId(fileResult.getId());
        chunkSetRow.setUpstreamProductId(preprocessProduct.getId());
        chunkSetRow.setChunkStrategyVersion(strategy.fullVersion());
        chunkSetRow.setChunkCount(chunkSet.getChunkCount());
        chunkSetRow.setTotalChars(chunkSet.getChunks().stream().mapToInt(Chunk::getCharCount).sum());
        chunkSetRow.setStatus(RowStatus.ACTIVE.name());
        chunkSetRow.setArtifactId(product.getArtifactId());
        chunkSetDbService.save(chunkSetRow);

        List<KbChunk> chunkRows = new ArrayList<>();
        for (Chunk chunk : chunkSet.getChunks()) {
            chunkRows.add(toKbChunk(chunkSetRow.getId(), strategy.fullVersion(), chunk));
        }
        chunkDbService.saveBatch(chunkRows, BATCH_SIZE);

        stepLogPersistence.save(task.getId(), outcome.getStepLogs());
        log.info("===> ChunkTaskRunner 切片完成, taskId={}, fileResultId={}, status={}, chunkCount={}, artifactId={}",
                task.getId(), fileResult.getId(), outcome.getSuggestedStatus(), chunkSet.getChunkCount(),
                product.getArtifactId());
    }

    private KbChunk toKbChunk(Long chunkSetId, String strategyVersion, Chunk chunk) {
        KbChunk row = new KbChunk();
        row.setChunkSetId(chunkSetId);
        row.setChunkId(chunk.getChunkId());
        row.setParentChunkId(chunk.getParentChunkId());
        row.setContent(chunk.getContent());
        row.setContentType(chunk.getContentType());
        row.setTitlePath(chunk.getTitlePath());
        row.setSourceElementIds(JsonUtil.toJsonStr(chunk.getSourceElementIds()));
        row.setPageRange(formatPageRange(chunk.getPageRange()));
        row.setTableRef(chunk.getTableRef());
        row.setOrderNo(chunk.getOrder());
        row.setCharCount(chunk.getCharCount());
        row.setTokenCount(chunk.getTokenCount());
        row.setStrategyVersion(strategyVersion);
        return row;
    }

    /** 页码范围落库：null → null；单页 "3"；连续 "1-3"；非连续压缩连续段逗号连接（无损等价，不截断） */
    static String formatPageRange(List<Integer> pages) {
        if (ObjectUtil.isNull(pages) || pages.isEmpty()) {
            return null;
        }
        List<Integer> distinct = pages.stream().distinct().sorted().toList();
        if (distinct.size() == 1) {
            return String.valueOf(distinct.getFirst());
        }
        StringBuilder sb = new StringBuilder();
        int start = distinct.getFirst();
        int prev = distinct.getFirst();
        for (int i = 1; i < distinct.size(); i++) {
            int cur = distinct.get(i);
            if (cur != prev + 1) {
                appendPageSegment(sb, start, prev);
                start = cur;
            }
            prev = cur;
        }
        appendPageSegment(sb, start, prev);
        return sb.toString();
    }

    /** 追加一段页码：单页 "3"，连续 "3-7"；非首段前置逗号 */
    private static void appendPageSegment(StringBuilder sb, int start, int end) {
        if (!sb.isEmpty()) {
            sb.append(',');
        }
        if (start == end) {
            sb.append(start);
        } else {
            sb.append(start).append('-').append(end);
        }
    }

    private void finishFailed(Long taskId, String errorCode, String errorMsg) {
        log.warn("===> ChunkTaskRunner 切片任务失败, taskId={}, errorCode={}, errorMsg={}",
                taskId, errorCode, errorMsg);
        pipelineTaskDbService.finish(taskId, PipelineTaskStatus.FAILED.name(), errorCode,
                StrUtil.isBlank(errorMsg) ? null : truncate(errorMsg));
    }

    private String truncate(String message) {
        return StrUtil.maxLength(message, 1000);
    }
}
