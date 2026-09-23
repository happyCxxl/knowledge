package com.knowledge.biz.service.impl;

import com.knowledge.biz.service.db.KbChunkDbService;
import com.knowledge.biz.service.db.KbChunkSetDbService;
import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineStepLogDbService;
import com.knowledge.biz.service.db.KbPipelineStrategyVersionDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.biz.service.support.ChunkVoAssembler;
import com.knowledge.biz.service.support.TaskDetailSupport;
import com.knowledge.biz.task.TaskQueueSupport;
import com.knowledge.biz.task.TaskTriggerSupport;
import com.knowledge.common.domain.entity.KbChunk;
import com.knowledge.common.domain.entity.KbChunkSet;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineStrategyVersion;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.dto.response.chunk.ChunkDetailVO;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import com.knowledge.worker.chunking.ChunkProperties;
import com.knowledge.worker.chunking.strategy.ChunkStrategy;
import com.knowledge.worker.chunking.strategy.ChunkStrategyParser;
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
 * 切片控制面服务单测：策略解析三档 / 上游产物校验 / 防重复用 / 建任务快照入队 / 切片详情。
 *
 * @author cxxl
 */
@ExtendWith(MockitoExtension.class)
class ChunkControlServiceImplTest {

    @Mock
    private KbFileResultDbService fileResultDbService;
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
    private KbChunkSetDbService chunkSetDbService;
    @Mock
    private KbChunkDbService chunkDbService;

    private ChunkControlServiceImpl service;

    @BeforeEach
    void setUp() {
        // 触发/详情助手为纯委托类、组装器为纯映射类，用真实实例（mock 会让 VO 组装返回 null，断言失真）
        service = new ChunkControlServiceImpl(fileResultDbService, pipelineProductDbService,
                stepLogDbService, strategyVersionDbService,
                new TaskTriggerSupport(pipelineTaskDbService, taskQueue),
                new TaskDetailSupport(pipelineTaskDbService),
                chunkSetDbService, chunkDbService,
                new ChunkStrategyParser(new ChunkProperties()),
                new ChunkVoAssembler());
    }

    private KbFileResult fileResult() {
        KbFileResult fileResult = new KbFileResult();
        fileResult.setId(10L);
        fileResult.setKnowledgeBaseId(10L);
        return fileResult;
    }

    private KbPipelineProduct preprocessProduct() {
        KbPipelineProduct product = new KbPipelineProduct();
        product.setId(50L);
        return product;
    }

    private void stubCommon() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        when(pipelineProductDbService.getByFileResultIdAndStage(10L, PipelineStage.PREPROCESS.name()))
                .thenReturn(preprocessProduct());
    }

    private KbPipelineStrategyVersion chunkVersion(String name, String version) {
        KbPipelineStrategyVersion row = new KbPipelineStrategyVersion();
        row.setId(66L);
        row.setType(ChunkStrategy.TYPE);
        row.setName(name);
        row.setVersion(version);
        row.setConfigSnapshot("{\"routes\":{},\"pipeline\":{\"titleInContent\":\"ON\"}}");
        row.setStatus("ACTIVE");
        return row;
    }

    @Test
    void shouldCreateTaskWithDefaultStrategySnapshotAndEnqueue() {
        stubCommon();
        when(strategyVersionDbService.getLatestEnabledByType(ChunkStrategy.TYPE)).thenReturn(null);
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.CHUNK.name()))
                .thenReturn(null);
        when(pipelineTaskDbService.save(any(KbPipelineTask.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbPipelineTask.class).setId(41L);
            return true;
        });

        var response = service.chunk(10L, null, null);

        assertEquals(41L, response.getPipelineTaskId());
        assertEquals("chunk-hybrid-v1", response.getStrategyVersion());
        ArgumentCaptor<KbPipelineTask> captor = ArgumentCaptor.forClass(KbPipelineTask.class);
        verify(pipelineTaskDbService).save(captor.capture());
        KbPipelineTask task = captor.getValue();
        assertEquals(PipelineStage.CHUNK.name(), task.getStage());
        assertEquals(PipelineTaskStatus.QUEUED.name(), task.getStatus());
        assertEquals(50L, task.getUpstreamProductId());
        assertNotNull(task.getStrategySnapshot());
        assertTrue(task.getStrategySnapshot().contains("parentChild"));
        verify(taskQueue).enqueue(41L);
    }

    @Test
    void missingPreprocessProductShouldThrow40432() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        when(strategyVersionDbService.getLatestEnabledByType(ChunkStrategy.TYPE)).thenReturn(null);
        when(pipelineProductDbService.getByFileResultIdAndStage(10L, PipelineStage.PREPROCESS.name()))
                .thenReturn(null);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.chunk(10L, null, null));
        assertEquals(ErrorCode.FILE_RESULT_NOT_FOUND, e.getErrorCode());
        verify(pipelineTaskDbService, never()).save(any());
        verify(taskQueue, never()).enqueue(any());
    }

    @Test
    void unknownStrategyVersionShouldThrow40433() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        when(strategyVersionDbService.getById(999L)).thenReturn(null);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.chunk(10L, 999L, null));
        assertEquals(ErrorCode.STRATEGY_VERSION_NOT_FOUND, e.getErrorCode());
        verify(pipelineProductDbService, never()).getByFileResultIdAndStage(any(), any());
    }

    @Test
    void explicitStrategyVersionShouldResolveFromDb() {
        stubCommon();
        when(strategyVersionDbService.getById(66L))
                .thenReturn(chunkVersion("chunk-strict", "v2"));
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.CHUNK.name()))
                .thenReturn(null);
        when(pipelineTaskDbService.save(any(KbPipelineTask.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbPipelineTask.class).setId(42L);
            return true;
        });

        var response = service.chunk(10L, 66L, null);

        assertEquals("chunk-strict-v2", response.getStrategyVersion());
        ArgumentCaptor<KbPipelineTask> captor = ArgumentCaptor.forClass(KbPipelineTask.class);
        verify(pipelineTaskDbService).save(captor.capture());
        assertTrue(captor.getValue().getStrategySnapshot().contains("titleInContent"));
    }

    @Test
    void mismatchedStrategyTypeShouldReject() {
        // 显式策略按行 id 引用，类型不匹配拒绝
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        KbPipelineStrategyVersion wrongType = chunkVersion("chunk-x", "v1");
        wrongType.setType("PREPROCESS");
        when(strategyVersionDbService.getById(66L)).thenReturn(wrongType);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.chunk(10L, 66L, null));
        assertEquals(ErrorCode.STRATEGY_VERSION_NOT_FOUND, e.getErrorCode());
    }

    @Test
    void runningShouldReject40431() {
        stubCommon();
        when(strategyVersionDbService.getLatestEnabledByType(ChunkStrategy.TYPE)).thenReturn(null);
        KbPipelineTask running = new KbPipelineTask();
        running.setId(41L);
        running.setStatus(PipelineTaskStatus.RUNNING.name());
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.CHUNK.name()))
                .thenReturn(running);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.chunk(10L, null, null));
        assertEquals(ErrorCode.TASK_ALREADY_PENDING, e.getErrorCode());
        verify(pipelineTaskDbService, never()).save(any());
        verify(taskQueue, never()).enqueue(any());
    }

    @Test
    void queuedShouldReuseExistingTask() {
        stubCommon();
        when(strategyVersionDbService.getLatestEnabledByType(ChunkStrategy.TYPE)).thenReturn(null);
        KbPipelineTask queued = new KbPipelineTask();
        queued.setId(41L);
        queued.setStatus(PipelineTaskStatus.QUEUED.name());
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.CHUNK.name()))
                .thenReturn(queued);

        var response = service.chunk(10L, null, null);

        assertEquals(41L, response.getPipelineTaskId());
        verify(pipelineTaskDbService, never()).save(any());
        verify(taskQueue).enqueue(41L);
    }

    @Test
    void detailShouldAssembleChunkSetSummaryAndChunks() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        KbPipelineTask task = new KbPipelineTask();
        task.setId(41L);
        task.setStage(PipelineStage.CHUNK.name());
        task.setStatus(PipelineTaskStatus.SUCCESS.name());
        task.setProductId(50L);
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.CHUNK.name())).thenReturn(task);
        KbPipelineProduct product = new KbPipelineProduct();
        product.setArtifactId("abc".repeat(16));
        when(pipelineProductDbService.getById(50L)).thenReturn(product);
        KbChunkSet chunkSet = new KbChunkSet();
        chunkSet.setId(70L);
        chunkSet.setChunkCount(2);
        chunkSet.setTotalChars(300);
        chunkSet.setArtifactId("abc".repeat(16));
        when(chunkSetDbService.getByArtifactId("abc".repeat(16))).thenReturn(chunkSet);
        KbChunk parent = new KbChunk();
        parent.setChunkId("c-1");
        parent.setContentType("SECTION");
        parent.setContent("章节父片");
        parent.setTitlePath("第一章");
        parent.setOrderNo(1);
        parent.setCharCount(200);
        KbChunk child = new KbChunk();
        child.setChunkId("c-1-1");
        child.setParentChunkId("c-1");
        child.setContentType("TABLE");
        child.setContent("表格子片");
        child.setOrderNo(2);
        child.setCharCount(100);
        when(chunkDbService.listByChunkSetId(70L)).thenReturn(List.of(parent, child));

        ChunkDetailVO detail = service.chunkDetail(10L, null);

        assertEquals(41L, detail.getTaskId());
        assertEquals(PipelineTaskStatus.SUCCESS.name(), detail.getStatus());
        assertNotNull(detail.getSummary());
        assertEquals(2, detail.getSummary().getChunkCount());
        assertEquals(300, detail.getSummary().getTotalChars());
        assertEquals(150, detail.getSummary().getAvgChars());
        assertEquals(1, detail.getSummary().getParentChunkCount());
        assertEquals(1, detail.getSummary().getChildChunkCount());
        assertEquals(2, detail.getSummary().getTypeCounts().size());
        assertEquals(2, detail.getChunks().size());
        assertEquals("c-1-1", detail.getChunks().get(1).getChunkId());
        assertEquals("c-1", detail.getChunks().get(1).getParentChunkId());
    }

    @Test
    void detailWithoutTaskShouldReturnEmptyChunks() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.CHUNK.name())).thenReturn(null);

        ChunkDetailVO detail = service.chunkDetail(10L, null);

        assertNotNull(detail.getChunks());
        assertTrue(detail.getChunks().isEmpty());
    }

    @Test
    void detailWithWrongStageTaskShouldReject() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        KbPipelineTask preprocessTask = new KbPipelineTask();
        preprocessTask.setId(31L);
        preprocessTask.setFileResultId(10L);
        preprocessTask.setStage(PipelineStage.PREPROCESS.name());
        when(pipelineTaskDbService.getById(31L)).thenReturn(preprocessTask);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.chunkDetail(10L, 31L));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
    }
}
