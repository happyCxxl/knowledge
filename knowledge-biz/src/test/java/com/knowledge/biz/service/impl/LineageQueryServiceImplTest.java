package com.knowledge.biz.service.impl;

import com.knowledge.biz.service.db.KbChunkSetDbService;
import com.knowledge.biz.service.db.KbEmbeddingSetDbService;
import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineStepLogDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.common.domain.entity.KbChunkSet;
import com.knowledge.common.domain.entity.KbEmbeddingSet;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineStepLog;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.dto.response.lineage.LineageVO;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 执行树聚合单测（step-12 B1）：多分支节点/血缘边/统计摘要/孤立节点/空文件/40432。
 *
 * @author cxxl
 */
@ExtendWith(MockitoExtension.class)
class LineageQueryServiceImplTest {

    @Mock
    private KbFileResultDbService fileResultDbService;
    @Mock
    private KbPipelineTaskDbService pipelineTaskDbService;
    @Mock
    private KbPipelineProductDbService pipelineProductDbService;
    @Mock
    private KbPipelineStepLogDbService stepLogDbService;
    @Mock
    private KbChunkSetDbService chunkSetDbService;
    @Mock
    private KbEmbeddingSetDbService embeddingSetDbService;

    private LineageQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new LineageQueryServiceImpl(fileResultDbService, pipelineTaskDbService,
                pipelineProductDbService, stepLogDbService, chunkSetDbService, embeddingSetDbService);
    }

    private KbPipelineTask task(Long id, String stage, Long upstreamProductId, Long productId, String snapshot) {
        KbPipelineTask task = new KbPipelineTask();
        task.setId(id);
        task.setFileResultId(10L);
        task.setStage(stage);
        task.setUpstreamProductId(upstreamProductId);
        task.setProductId(productId);
        task.setStrategySnapshot(snapshot);
        task.setStatus(PipelineTaskStatus.SUCCESS.name());
        return task;
    }

    private KbPipelineProduct product(Long id, String stage, String artifactId, String capability) {
        KbPipelineProduct product = new KbPipelineProduct();
        product.setId(id);
        product.setFileResultId(10L);
        product.setStage(stage);
        product.setArtifactId(artifactId);
        product.setContentHash(artifactId);
        product.setCapabilitySnapshot(capability);
        return product;
    }

    @Test
    void shouldAssembleNodesEdgesAndStats() {
        when(fileResultDbService.getById(10L)).thenReturn(new KbFileResult());
        // 多分支：parse→structure→{preproc1→chunk1→embed1, preproc2→chunk2}
        when(pipelineTaskDbService.listByFileResultId(10L)).thenReturn(List.of(
                task(20L, PipelineStage.PARSE.name(), null, 10L, null),
                task(51L, PipelineStage.STRUCTURE.name(), 10L, 20L, null),
                task(61L, PipelineStage.PREPROCESS.name(), 20L, 30L,
                        "{\"name\":\"preproc-default\",\"version\":\"v1\"}"),
                task(62L, PipelineStage.PREPROCESS.name(), 20L, 60L,
                        "{\"name\":\"preproc-strict\",\"version\":\"v2\"}"),
                task(71L, PipelineStage.CHUNK.name(), 30L, 40L,
                        "{\"name\":\"chunk-hybrid\",\"version\":\"v1\"}"),
                task(72L, PipelineStage.CHUNK.name(), 60L, 70L,
                        "{\"name\":\"chunk-hybrid\",\"version\":\"v1\"}"),
                task(81L, PipelineStage.EMBED.name(), 40L, 50L,
                        "{\"name\":\"embed-default\",\"version\":\"v1\"}")));
        when(pipelineProductDbService.listByFileResultId(10L)).thenReturn(List.of(
                product(10L, PipelineStage.PARSE.name(), "art-p", "{\"pdfbox\":\"3.0.4\"}"),
                product(20L, PipelineStage.STRUCTURE.name(), "art-s", "{\"rules\":\"v1\"}"),
                product(30L, PipelineStage.PREPROCESS.name(), "art-p1", null),
                product(60L, PipelineStage.PREPROCESS.name(), "art-p2", null),
                product(40L, PipelineStage.CHUNK.name(), "art-c1", null),
                product(70L, PipelineStage.CHUNK.name(), "art-c2", null),
                product(50L, PipelineStage.EMBED.name(), "art-e1", null)));
        KbChunkSet chunkSet1 = new KbChunkSet();
        chunkSet1.setArtifactId("art-c1");
        chunkSet1.setChunkCount(156);
        when(chunkSetDbService.listByFileResultId(10L)).thenReturn(List.of(chunkSet1));
        KbEmbeddingSet embedSet1 = new KbEmbeddingSet();
        embedSet1.setArtifactId("art-e1");
        embedSet1.setRecordCount(782);
        embedSet1.setCachedCount(12);
        when(embeddingSetDbService.listByFileResultId(10L)).thenReturn(List.of(embedSet1));
        KbPipelineStepLog step1 = new KbPipelineStepLog();
        step1.setTaskId(61L);
        step1.setMatchedCount(1200);
        step1.setChangedCount(64);
        when(stepLogDbService.listByTaskIds(List.of(61L, 62L))).thenReturn(List.of(step1));

        LineageVO vo = service.lineage(10L);

        assertNotNull(vo.getNodes());
        assertEquals(7, vo.getNodes().size());
        // 环节顺序：PARSE, STRUCTURE, PREPROCESS×2, CHUNK×2, EMBED
        assertEquals("PARSE", vo.getNodes().get(0).getStage());
        assertEquals("STRUCTURE", vo.getNodes().get(1).getStage());
        assertEquals("PREPROCESS", vo.getNodes().get(2).getStage());
        assertEquals("PREPROCESS", vo.getNodes().get(3).getStage());
        assertEquals("CHUNK", vo.getNodes().get(4).getStage());
        assertEquals("CHUNK", vo.getNodes().get(5).getStage());
        assertEquals("EMBED", vo.getNodes().get(6).getStage());
        // 策略版本解析（有策略环节）
        assertEquals("preproc-default-v1", vo.getNodes().get(2).getStrategyVersion());
        assertEquals("preproc-strict-v2", vo.getNodes().get(3).getStrategyVersion());
        assertEquals("chunk-hybrid-v1", vo.getNodes().get(4).getStrategyVersion());
        // 产物 ID（前端「以此产物触发下游」传此值，非任务 ID）
        assertEquals(10L, vo.getNodes().get(0).getProductId());
        assertEquals(20L, vo.getNodes().get(1).getProductId());
        assertEquals(30L, vo.getNodes().get(2).getProductId());
        assertEquals(60L, vo.getNodes().get(3).getProductId());
        assertEquals(40L, vo.getNodes().get(4).getProductId());
        assertEquals(70L, vo.getNodes().get(5).getProductId());
        assertEquals(50L, vo.getNodes().get(6).getProductId());
        // 能力快照（无策略环节）
        assertTrue(vo.getNodes().get(1).getCapability().contains("rules"));
        // 统计摘要
        assertEquals("156", vo.getNodes().get(4).getStats().get("chunkCount"));
        assertEquals("782", vo.getNodes().get(6).getStats().get("recordCount"));
        assertEquals("12", vo.getNodes().get(6).getStats().get("cachedCount"));
        assertEquals("1200", vo.getNodes().get(2).getStats().get("matched"));
        assertEquals("64", vo.getNodes().get(2).getStats().get("changed"));
        // 血缘边：p→s, s→pr1, s→pr2, pr1→c1, pr2→c2, c1→e1 = 6 条
        assertEquals(6, vo.getEdges().size());
        assertEquals(20L, vo.getEdges().get(0).getFromTaskId());
        assertEquals(51L, vo.getEdges().get(0).getToTaskId());
    }

    @Test
    void shouldReturnEmptyForFileWithoutTasks() {
        when(fileResultDbService.getById(10L)).thenReturn(new KbFileResult());
        when(pipelineTaskDbService.listByFileResultId(10L)).thenReturn(List.of());
        when(pipelineProductDbService.listByFileResultId(10L)).thenReturn(List.of());
        when(chunkSetDbService.listByFileResultId(10L)).thenReturn(List.of());
        when(embeddingSetDbService.listByFileResultId(10L)).thenReturn(List.of());

        LineageVO vo = service.lineage(10L);

        assertEquals(0, vo.getNodes().size());
        assertEquals(0, vo.getEdges().size());
    }

    @Test
    void missingUpstreamProductShouldNotCreateEdge() {
        when(fileResultDbService.getById(10L)).thenReturn(new KbFileResult());
        // chunk 任务指向不存在的上游产物 → 孤立节点、无对应边
        when(pipelineTaskDbService.listByFileResultId(10L)).thenReturn(List.of(
                task(71L, PipelineStage.CHUNK.name(), 999L, 40L,
                        "{\"name\":\"chunk-hybrid\",\"version\":\"v1\"}")));
        when(pipelineProductDbService.listByFileResultId(10L)).thenReturn(List.of(
                product(40L, PipelineStage.CHUNK.name(), "art-c1", null)));
        when(chunkSetDbService.listByFileResultId(10L)).thenReturn(List.of());
        when(embeddingSetDbService.listByFileResultId(10L)).thenReturn(List.of());

        LineageVO vo = service.lineage(10L);

        assertEquals(1, vo.getNodes().size());
        assertEquals(0, vo.getEdges().size());
    }

    @Test
    void missingFileResultShouldReject40432() {
        when(fileResultDbService.getById(10L)).thenReturn(null);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.lineage(10L));
        assertEquals(ErrorCode.FILE_RESULT_NOT_FOUND, e.getErrorCode());
    }
}
