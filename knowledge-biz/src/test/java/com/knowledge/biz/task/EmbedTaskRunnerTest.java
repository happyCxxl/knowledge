package com.knowledge.biz.task;

import com.knowledge.biz.service.IndexSetService;
import com.knowledge.biz.service.db.KbChunkSetDbService;
import com.knowledge.biz.service.db.KbEmbeddingRecordDbService;
import com.knowledge.biz.service.db.KbEmbeddingSetDbService;
import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineStepLogDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.common.domain.chunk.Chunk;
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
import com.knowledge.common.domain.task.StepLogInfo;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.PipelineTaskErrorCode;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.filecenter.service.FileStorage;
import com.knowledge.model.catalog.StaticModelCatalog;
import com.knowledge.worker.chunking.ChunkProperties;
import com.knowledge.worker.chunking.strategy.ChunkStrategyParser;
import com.knowledge.worker.embedding.EmbedContext;
import com.knowledge.worker.embedding.EmbedProperties;
import com.knowledge.worker.embedding.EmbedderPort;
import com.knowledge.worker.embedding.strategy.EmbedStrategyParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 向量化任务执行器单测：成功落库链（EmbeddingSet 产物 + product + 两表分批 + 子步骤）/
 * 上游切片产物缺失 / 管线失败 / 复用候选账本加载（cacheOn/Off）。
 *
 * @author cxxl
 */
@ExtendWith(MockitoExtension.class)
class EmbedTaskRunnerTest {

    @Mock
    private KbPipelineTaskDbService pipelineTaskDbService;
    @Mock
    private KbFileResultDbService fileResultDbService;
    @Mock
    private KbPipelineProductDbService pipelineProductDbService;
    @Mock
    private KbPipelineStepLogDbService stepLogDbService;
    @Mock
    private KbChunkSetDbService chunkSetDbService;
    @Mock
    private KbEmbeddingSetDbService embeddingSetDbService;
    @Mock
    private KbEmbeddingRecordDbService embeddingRecordDbService;
    @Mock
    private FileStorage fileStorage;
    @Mock
    private EmbedderPort embedder;
    @Mock
    private IndexSetService indexSetService;

    private EmbedTaskRunner runner;

    @BeforeEach
    void setUp() {
        EmbedProperties embedProperties = new EmbedProperties();
        runner = new EmbedTaskRunner(pipelineTaskDbService, fileResultDbService,
                pipelineProductDbService, chunkSetDbService, embeddingSetDbService,
                embeddingRecordDbService, fileStorage, indexSetService, embedder,
                new EmbedStrategyParser(embedProperties, new StaticModelCatalog()),
                new ChunkStrategyParser(new ChunkProperties()),
                embedProperties, new ChunkProperties(),
                new StepLogPersistence(stepLogDbService),
                new ProductPersistence(pipelineProductDbService, pipelineTaskDbService, fileStorage));
    }

    private KbPipelineTask queuedTask() {
        KbPipelineTask task = new KbPipelineTask();
        task.setId(70L);
        task.setFileResultId(10L);
        task.setStage(PipelineStage.EMBED.name());
        task.setStatus(PipelineTaskStatus.QUEUED.name());
        return task;
    }

    private KbPipelineProduct chunkProduct() {
        KbPipelineProduct product = new KbPipelineProduct();
        product.setId(50L);
        product.setArtifactId("abc".repeat(22));
        return product;
    }

    private ChunkSet chunkSet() {
        ChunkSet chunkSet = new ChunkSet();
        chunkSet.setChunkSetId("cs-doc-10-chunk-hybrid-v1");
        chunkSet.setFileResultId(10L);
        Chunk chunk = new Chunk();
        chunk.setChunkId("chunk-0001");
        chunk.setContent("正文片");
        chunk.setContentType("PARAGRAPH");
        chunk.setCharCount(3);
        chunkSet.setChunks(new ArrayList<>(List.of(chunk)));
        chunkSet.setChunkCount(1);
        return chunkSet;
    }

    private EmbedOutcome successOutcome() {
        EmbedOutcome outcome = new EmbedOutcome();
        outcome.setSuggestedStatus(PipelineTaskStatus.SUCCESS.name());
        EmbeddingSet set = new EmbeddingSet();
        set.setEmbeddingSetId("es-cs-doc-10-embed-default-v1");
        set.setFileResultId(10L);
        set.setChunkSetRef(60L);
        set.setChunkSetId("cs-doc-10-chunk-hybrid-v1");
        set.setStrategyVersion("embed-default-v1");
        set.setModel("text-embedding-v4");
        set.setDimension(1024);
        set.setMetric("COSINE");
        set.setNormalized(true);
        set.setRecordCount(2);
        set.setCachedCount(1);
        EmbeddingRecord cached = new EmbeddingRecord();
        cached.setEmbeddingId("emb-0001");
        cached.setChunkId("chunk-0001");
        cached.setContentType("PARAGRAPH");
        cached.setInputText("正文片");
        cached.setInputTextHash("hash-1");
        cached.setTokenCount(2);
        cached.setStatus("CACHED");
        cached.setCacheHit(true);
        cached.setVector(List.of(1.0f));
        EmbeddingRecord fresh = new EmbeddingRecord();
        fresh.setEmbeddingId("emb-0002");
        fresh.setChunkId("chunk-0002");
        fresh.setContentType("PARAGRAPH");
        fresh.setInputText("新片");
        fresh.setInputTextHash("hash-2");
        fresh.setTokenCount(2);
        fresh.setStatus("SUCCESS");
        fresh.setRequestId("req-1");
        set.setRecords(List.of(cached, fresh));
        outcome.setEmbeddingSet(set);
        StepLogInfo step = new StepLogInfo();
        step.setStepName("复用判定");
        step.setStatus("SUCCESS");
        step.setCapabilityVersion("ledger-reuse-v1");
        step.setMatchedCount(1);
        outcome.setStepLogs(List.of(step));
        return outcome;
    }

    /** 领任务 + 文件结果 + 上游 CHUNK 产物（含切片归档读取）——所有成功/失败路径共用 */
    private void stubClaimAndUpstream() {
        when(pipelineTaskDbService.getById(70L)).thenReturn(queuedTask());
        when(pipelineTaskDbService.claim(70L)).thenReturn(1);
        KbFileResult fileResult = new KbFileResult();
        fileResult.setId(10L);
        fileResult.setSourceFileId(1L);
        when(fileResultDbService.getById(10L)).thenReturn(fileResult);
        when(pipelineProductDbService.getByFileResultIdAndStage(10L, PipelineStage.CHUNK.name()))
                .thenReturn(chunkProduct());
        when(fileStorage.getObject("abc".repeat(22)))
                .thenReturn(Objects.requireNonNull(JsonUtil.toJsonStr(chunkSet())).getBytes(StandardCharsets.UTF_8));
    }

    /** 成功落库链专有桩：切片集合引用 + 产物写回 + product 行 ID */
    private void stubPersist() {
        KbChunkSet chunkSetRow = new KbChunkSet();
        chunkSetRow.setId(60L);
        when(chunkSetDbService.getLatestByFileResultId(10L)).thenReturn(chunkSetRow);
        when(fileStorage.putObject(any(byte[].class))).thenReturn("def".repeat(22));
        when(pipelineProductDbService.save(any(KbPipelineProduct.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbPipelineProduct.class).setId(80L);
            return true;
        });
    }

    @Test
    void shouldPersistProductAndFinishSuccess() {
        stubClaimAndUpstream();
        stubPersist();
        when(embedder.embed(any(EmbedContext.class))).thenReturn(successOutcome());

        runner.run(70L);

        ArgumentCaptor<KbPipelineProduct> productCaptor = ArgumentCaptor.forClass(KbPipelineProduct.class);
        verify(pipelineProductDbService).save(productCaptor.capture());
        assertEquals(PipelineStage.EMBED.name(), productCaptor.getValue().getStage());
        assertEquals(50L, productCaptor.getValue().getUpstreamProductId());
        verify(pipelineTaskDbService).updateProductId(eq(70L), anyLong());

        ArgumentCaptor<KbEmbeddingSet> setCaptor = ArgumentCaptor.forClass(KbEmbeddingSet.class);
        verify(embeddingSetDbService).save(setCaptor.capture());
        assertEquals("es-cs-doc-10-embed-default-v1", setCaptor.getValue().getEmbeddingSetId());
        assertEquals(1024, setCaptor.getValue().getDimension());
        assertEquals(1, setCaptor.getValue().getCachedCount());

        verify(embeddingRecordDbService).saveBatch(any(), eq(500));
        verify(stepLogDbService).save(any());
        verify(pipelineTaskDbService).finish(70L, PipelineTaskStatus.SUCCESS.name(), null, null);
        verify(indexSetService).onFileProductsReady(10L);
    }

    @Test
    void missingChunkProductShouldFailEmbedEmpty() {
        when(pipelineTaskDbService.getById(70L)).thenReturn(queuedTask());
        when(pipelineTaskDbService.claim(70L)).thenReturn(1);
        KbFileResult fileResult = new KbFileResult();
        fileResult.setId(10L);
        when(fileResultDbService.getById(10L)).thenReturn(fileResult);
        when(pipelineProductDbService.getByFileResultIdAndStage(10L, PipelineStage.CHUNK.name()))
                .thenReturn(null);

        runner.run(70L);

        verify(pipelineTaskDbService).finish(70L, PipelineTaskStatus.FAILED.name(),
                PipelineTaskErrorCode.EMBED_EMPTY.name(), "切片产物不存在，请先触发切片");
        verify(fileStorage, never()).putObject(any(byte[].class));
        verify(embedder, never()).embed(any());
    }

    @Test
    void pipelineFailureShouldFinishFailed() {
        stubClaimAndUpstream();
        EmbedOutcome failed = new EmbedOutcome();
        failed.setSuggestedStatus(PipelineTaskStatus.FAILED.name());
        failed.setErrorCode(PipelineTaskErrorCode.EMBED_CONSISTENCY_FAILED.name());
        failed.setErrorMsg("维度不一致");
        when(embedder.embed(any(EmbedContext.class))).thenReturn(failed);

        runner.run(70L);

        verify(pipelineTaskDbService).finish(70L, PipelineTaskStatus.FAILED.name(),
                PipelineTaskErrorCode.EMBED_CONSISTENCY_FAILED.name(), "维度不一致");
        verify(fileStorage, never()).putObject(any(byte[].class));
    }

    @Test
    void reuseCandidatesShouldBeLoadedWhenCacheOn() {
        stubClaimAndUpstream();
        stubPersist();
        KbEmbeddingSet historyRow = new KbEmbeddingSet();
        historyRow.setEmbeddingSetId("es-old");
        historyRow.setArtifactId("hist".repeat(21));
        when(embeddingSetDbService.listHistoryByFileResultIdAndStrategyVersion(10L, "embed-default-v1", 10))
                .thenReturn(List.of(historyRow));
        EmbeddingSet historySet = new EmbeddingSet();
        historySet.setEmbeddingSetId("es-old");
        when(fileStorage.getObject("hist".repeat(21)))
                .thenReturn(Objects.requireNonNull(JsonUtil.toJsonStr(historySet)).getBytes(StandardCharsets.UTF_8));
        when(embedder.embed(any(EmbedContext.class))).thenReturn(successOutcome());

        runner.run(70L);

        ArgumentCaptor<EmbedContext> contextCaptor = ArgumentCaptor.forClass(EmbedContext.class);
        verify(embedder).embed(contextCaptor.capture());
        assertEquals(1, contextCaptor.getValue().getReuseCandidates().size());
        assertEquals("es-old", contextCaptor.getValue().getReuseCandidates().getFirst().getEmbeddingSetId());
    }

    @Test
    void reuseCandidatesShouldNotBeLoadedWhenCacheOff() {
        when(pipelineTaskDbService.getById(70L)).thenReturn(taskWithCacheOff());
        when(pipelineTaskDbService.claim(70L)).thenReturn(1);
        KbFileResult fileResult = new KbFileResult();
        fileResult.setId(10L);
        fileResult.setSourceFileId(1L);
        when(fileResultDbService.getById(10L)).thenReturn(fileResult);
        when(pipelineProductDbService.getByFileResultIdAndStage(10L, PipelineStage.CHUNK.name()))
                .thenReturn(chunkProduct());
        when(fileStorage.getObject("abc".repeat(22)))
                .thenReturn(Objects.requireNonNull(JsonUtil.toJsonStr(chunkSet())).getBytes(StandardCharsets.UTF_8));
        stubPersist();
        when(embedder.embed(any(EmbedContext.class))).thenReturn(successOutcome());

        runner.run(70L);

        verify(embeddingSetDbService, never()).listHistoryByFileResultIdAndStrategyVersion(anyLong(), anyString(), eq(10));
        ArgumentCaptor<EmbedContext> contextCaptor = ArgumentCaptor.forClass(EmbedContext.class);
        verify(embedder).embed(contextCaptor.capture());
        assertTrue(contextCaptor.getValue().getReuseCandidates().isEmpty());
    }

    private KbPipelineTask taskWithCacheOff() {
        KbPipelineTask task = queuedTask();
        task.setStrategySnapshot("{\"model\":\"text-embedding-v4\",\"cacheEnabled\":\"OFF\"}");
        return task;
    }

    @Test
    void persistShouldBatchRecordsAndFinishPartialSuccess() {
        stubClaimAndUpstream();
        stubPersist();
        EmbedOutcome outcome = successOutcome();
        outcome.setSuggestedStatus(PipelineTaskStatus.PARTIAL_SUCCESS.name());
        when(embedder.embed(any(EmbedContext.class))).thenReturn(outcome);

        runner.run(70L);

        verify(pipelineTaskDbService).finish(70L, PipelineTaskStatus.PARTIAL_SUCCESS.name(), null, null);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<KbEmbeddingRecord>> batchCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(embeddingRecordDbService).saveBatch(batchCaptor.capture(), eq(500));
        assertEquals(2, batchCaptor.getValue().size());
    }

    @Test
    void finishedTaskShouldBeSkipped() {
        KbPipelineTask finished = queuedTask();
        finished.setStatus(PipelineTaskStatus.SUCCESS.name());
        when(pipelineTaskDbService.getById(70L)).thenReturn(finished);

        runner.run(70L);

        verify(pipelineTaskDbService, never()).claim(anyLong());
        verify(embedder, never()).embed(any());
    }
}
