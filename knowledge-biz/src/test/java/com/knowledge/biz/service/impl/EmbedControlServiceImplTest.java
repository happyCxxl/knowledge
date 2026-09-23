package com.knowledge.biz.service.impl;

import com.knowledge.biz.service.db.KbEmbeddingRecordDbService;
import com.knowledge.biz.service.db.KbEmbeddingSetDbService;
import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KnowledgeBaseDbService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineStepLogDbService;
import com.knowledge.biz.service.db.KbPipelineStrategyVersionDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.biz.service.db.KbStrategyBindingDbService;
import com.knowledge.biz.service.support.EmbedVoAssembler;
import com.knowledge.biz.service.support.TaskDetailSupport;
import com.knowledge.biz.task.TaskQueueSupport;
import com.knowledge.biz.task.TaskTriggerSupport;
import com.knowledge.common.domain.entity.KbEmbeddingRecord;
import com.knowledge.common.domain.entity.KbEmbeddingSet;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineStrategyVersion;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.entity.KbStrategyBinding;
import com.knowledge.common.dto.response.embed.EmbedDetailVO;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import com.knowledge.model.catalog.StaticModelCatalog;
import com.knowledge.worker.chunking.ChunkProperties;
import com.knowledge.worker.chunking.strategy.ChunkStrategyParser;
import com.knowledge.worker.embedding.EmbedProperties;
import com.knowledge.worker.embedding.strategy.EmbedStrategy;
import com.knowledge.worker.embedding.strategy.EmbedStrategyParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 向量化控制面服务单测：策略解析四档（显式/KB 绑定/最新启用/内置默认）/ 上游切片产物校验 /
 * 窗口前置校验（触发时明确报错）/ 防重复用 / 建任务快照入队 / 详情组装。
 *
 * @author cxxl
 */
@ExtendWith(MockitoExtension.class)
class EmbedControlServiceImplTest {

    @Mock
    private KbFileResultDbService fileResultDbService;
    @Mock
    private KnowledgeBaseDbService knowledgeBaseDbService;
    @Mock
    private KbPipelineTaskDbService pipelineTaskDbService;
    @Mock
    private KbPipelineProductDbService pipelineProductDbService;
    @Mock
    private KbPipelineStrategyVersionDbService strategyVersionDbService;
    @Mock
    private KbPipelineStepLogDbService stepLogDbService;
    @Mock
    private TaskQueueSupport taskQueue;
    @Mock
    private KbEmbeddingSetDbService embeddingSetDbService;
    @Mock
    private KbEmbeddingRecordDbService embeddingRecordDbService;
    @Mock
    private KbStrategyBindingDbService strategyBindingDbService;

    private EmbedControlServiceImpl service;

    @BeforeEach
    void setUp() {
        // 触发/详情助手为纯委托类、组装器为纯映射类，用真实实例（mock 会让 VO 组装返回 null，断言失真）
        service = new EmbedControlServiceImpl(fileResultDbService, knowledgeBaseDbService,
                pipelineProductDbService, strategyVersionDbService, stepLogDbService,
                embeddingSetDbService, embeddingRecordDbService, new EmbedVoAssembler(),
                new EmbedStrategyParser(new EmbedProperties(), new StaticModelCatalog()),
                new ChunkStrategyParser(new ChunkProperties()),
                new EmbedProperties(), new ChunkProperties(), strategyBindingDbService,
                new TaskTriggerSupport(pipelineTaskDbService, taskQueue),
                new TaskDetailSupport(pipelineTaskDbService));
    }

    private KbFileResult fileResult() {
        KbFileResult fileResult = new KbFileResult();
        fileResult.setId(10L);
        fileResult.setKnowledgeBaseId(10L);
        return fileResult;
    }

    /** CHUNK 产物（capabilitySnapshot 可指定切片策略快照；空=跳过窗口预检） */
    private KbPipelineProduct chunkProduct(String capabilitySnapshot) {
        KbPipelineProduct product = new KbPipelineProduct();
        product.setId(50L);
        product.setCapabilitySnapshot(capabilitySnapshot);
        return product;
    }

    private void stubCommon() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        when(pipelineProductDbService.getByFileResultIdAndStage(10L, PipelineStage.CHUNK.name()))
                .thenReturn(chunkProduct(null));
    }

    @Test
    void shouldCreateTaskWithBuiltinDefaultStrategyAndEnqueue() {
        stubCommon();
        when(strategyVersionDbService.getLatestEnabledByType(EmbedStrategy.TYPE)).thenReturn(null);
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.EMBED.name()))
                .thenReturn(null);
        when(pipelineTaskDbService.save(any(KbPipelineTask.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbPipelineTask.class).setId(41L);
            return true;
        });

        var response = service.embed(10L, null, null);

        assertEquals(41L, response.getPipelineTaskId());
        assertEquals("embed-default-v1", response.getStrategyVersion());
        ArgumentCaptor<KbPipelineTask> captor = ArgumentCaptor.forClass(KbPipelineTask.class);
        verify(pipelineTaskDbService).save(captor.capture());
        KbPipelineTask task = captor.getValue();
        assertEquals(PipelineStage.EMBED.name(), task.getStage());
        assertEquals(PipelineTaskStatus.QUEUED.name(), task.getStatus());
        assertEquals(50L, task.getUpstreamProductId());
        assertNotNull(task.getStrategySnapshot());
        assertTrue(task.getStrategySnapshot().contains("text-embedding-v4"));
        verify(taskQueue).enqueue(41L);
    }

    @Test
    void missingChunkProductShouldThrow40434() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        when(strategyVersionDbService.getLatestEnabledByType(EmbedStrategy.TYPE)).thenReturn(null);
        when(pipelineProductDbService.getByFileResultIdAndStage(10L, PipelineStage.CHUNK.name()))
                .thenReturn(null);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.embed(10L, null, null));
        assertEquals(ErrorCode.EMBED_UPSTREAM_MISSING, e.getErrorCode());
        verify(pipelineTaskDbService, never()).save(any());
        verify(taskQueue, never()).enqueue(any());
    }

    @Test
    void unknownStrategyVersionShouldThrow40433() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        when(strategyVersionDbService.getById(999L)).thenReturn(null);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.embed(10L, 999L, null));
        assertEquals(ErrorCode.STRATEGY_VERSION_NOT_FOUND, e.getErrorCode());
    }

    @Test
    void boundStrategyShouldWinOverLatestEnabled() {
        stubCommon();
        KbStrategyBinding binding = new KbStrategyBinding();
        binding.setStrategyVersionId(77L);
        when(strategyBindingDbService.getByKbAndType(10L, EmbedStrategy.TYPE)).thenReturn(binding);
        when(strategyVersionDbService.getById(77L)).thenReturn(embedVersion(77L, "embed-bound", "v3", "ACTIVE"));
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.EMBED.name()))
                .thenReturn(null);
        when(pipelineTaskDbService.save(any(KbPipelineTask.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbPipelineTask.class).setId(43L);
            return true;
        });

        var response = service.embed(10L, null, null);

        assertEquals("embed-bound-v3", response.getStrategyVersion());
    }

    @Test
    void windowIncompatibleShouldThrow40435() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        when(strategyVersionDbService.getLatestEnabledByType(EmbedStrategy.TYPE)).thenReturn(null);
        // 切片策略 softMaxLen=20000 超 text-embedding-v4 窗口（8192×1.5=12288）
        when(pipelineProductDbService.getByFileResultIdAndStage(10L, PipelineStage.CHUNK.name()))
                .thenReturn(chunkProduct("{\"routes\":{\"body\":{\"algorithm\":\"paragraph-aggregate\","
                        + "\"params\":{\"softMaxLen\":20000}}}}"));

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.embed(10L, null, null));
        assertEquals(ErrorCode.EMBED_MODEL_INCOMPATIBLE, e.getErrorCode());
        verify(pipelineTaskDbService, never()).save(any());
        verify(taskQueue, never()).enqueue(any());
    }

    @Test
    void windowCompatibleShouldProceed() {
        stubCommon();
        when(strategyVersionDbService.getLatestEnabledByType(EmbedStrategy.TYPE)).thenReturn(null);
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.EMBED.name()))
                .thenReturn(null);
        when(pipelineTaskDbService.save(any(KbPipelineTask.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbPipelineTask.class).setId(44L);
            return true;
        });

        var response = service.embed(10L, null, null);

        assertEquals(44L, response.getPipelineTaskId());
    }

    @Test
    void runningShouldReject40431() {
        stubCommon();
        when(strategyVersionDbService.getLatestEnabledByType(EmbedStrategy.TYPE)).thenReturn(null);
        KbPipelineTask running = new KbPipelineTask();
        running.setId(41L);
        running.setStatus(PipelineTaskStatus.RUNNING.name());
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.EMBED.name()))
                .thenReturn(running);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.embed(10L, null, null));
        assertEquals(ErrorCode.TASK_ALREADY_PENDING, e.getErrorCode());
        verify(pipelineTaskDbService, never()).save(any());
    }

    @Test
    void queuedShouldReuseExistingTask() {
        stubCommon();
        when(strategyVersionDbService.getLatestEnabledByType(EmbedStrategy.TYPE)).thenReturn(null);
        KbPipelineTask queued = new KbPipelineTask();
        queued.setId(41L);
        queued.setStatus(PipelineTaskStatus.QUEUED.name());
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.EMBED.name()))
                .thenReturn(queued);

        var response = service.embed(10L, null, null);

        assertEquals(41L, response.getPipelineTaskId());
        verify(pipelineTaskDbService, never()).save(any());
        verify(taskQueue).enqueue(41L);
    }

    @Test
    void detailShouldAssembleSummaryAndRecords() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        KbPipelineTask task = new KbPipelineTask();
        task.setId(41L);
        task.setStage(PipelineStage.EMBED.name());
        task.setStatus(PipelineTaskStatus.SUCCESS.name());
        task.setProductId(50L);
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.EMBED.name())).thenReturn(task);
        KbPipelineProduct product = new KbPipelineProduct();
        product.setArtifactId("abc".repeat(16));
        when(pipelineProductDbService.getById(50L)).thenReturn(product);
        KbEmbeddingSet set = new KbEmbeddingSet();
        set.setId(70L);
        set.setEmbeddingSetId("es-cs-1-embed-default-v1");
        set.setStrategyVersion("embed-default-v1");
        set.setModel("text-embedding-v4");
        set.setDimension(1024);
        set.setMetric("COSINE");
        set.setNormalized(true);
        set.setRecordCount(3);
        set.setCachedCount(1);
        set.setArtifactId("abc".repeat(16));
        when(embeddingSetDbService.getByArtifactId("abc".repeat(16))).thenReturn(set);
        KbEmbeddingRecord cached = new KbEmbeddingRecord();
        cached.setEmbeddingId("emb-0001");
        cached.setStatus("CACHED");
        cached.setCacheHit(true);
        KbEmbeddingRecord fresh = new KbEmbeddingRecord();
        fresh.setEmbeddingId("emb-0002");
        fresh.setStatus("SUCCESS");
        KbEmbeddingRecord skipped = new KbEmbeddingRecord();
        skipped.setEmbeddingId("emb-0003");
        skipped.setStatus("SKIPPED");
        when(embeddingRecordDbService.listByEmbeddingSetId(70L)).thenReturn(List.of(cached, fresh, skipped));

        EmbedDetailVO detail = service.embedDetail(10L, null);

        assertEquals(41L, detail.getTaskId());
        assertNotNull(detail.getSummary());
        assertEquals(3, detail.getSummary().getRecordCount());
        assertEquals(1, detail.getSummary().getCachedCount());
        assertEquals(1, detail.getSummary().getSuccessCount());
        assertEquals(0, detail.getSummary().getFailedCount());
        assertEquals(1, detail.getSummary().getSkippedCount());
        assertEquals(3, detail.getRecords().size());
    }

    @Test
    void detailWithoutTaskShouldReturnEmptyRecords() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.EMBED.name())).thenReturn(null);

        EmbedDetailVO detail = service.embedDetail(10L, null);

        assertNotNull(detail.getRecords());
        assertTrue(detail.getRecords().isEmpty());
    }

    private KbPipelineStrategyVersion embedVersion(Long id, String name, String version, String status) {
        KbPipelineStrategyVersion row = new KbPipelineStrategyVersion();
        row.setId(id);
        row.setType(EmbedStrategy.TYPE);
        row.setName(name);
        row.setVersion(version);
        row.setConfigSnapshot("{\"model\":\"text-embedding-v4\"}");
        row.setStatus(status);
        return row;
    }
}
