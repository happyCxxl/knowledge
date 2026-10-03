package com.knowledge.biz.service.impl;

import com.knowledge.biz.service.db.KbChunkSetDbService;
import com.knowledge.biz.service.db.KbEmbeddingSetDbService;
import com.knowledge.biz.service.db.KnowledgeBaseDbService;
import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineStepLogDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.biz.testkit.SecurityTestSupport;
import com.knowledge.common.domain.entity.KnowledgeBase;
import com.knowledge.common.domain.entity.KbChunkSet;
import com.knowledge.common.domain.entity.KbEmbeddingSet;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineStepLog;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.input.FileReference;
import com.knowledge.common.domain.parse.ParseElement;
import com.knowledge.common.domain.parse.ParseResult;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.domain.parse.QualityInfo;
import com.knowledge.common.dto.response.lineage.LineageNodeVO;
import com.knowledge.common.dto.response.lineage.LineageVO;
import com.knowledge.common.enums.parse.ElementType;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.biz.service.support.FileResultAccessGuard;
import com.knowledge.filecenter.service.FileStorage;
import com.knowledge.worker.chunking.ChunkProperties;
import com.knowledge.worker.chunking.strategy.ChunkStrategyParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 执行树聚合单测：多分支节点/血缘边/统计摘要/解析环节统计/产物不可读/未完成任务的节点可见性/孤立节点/空文件/40432。
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
    @Mock
    private KnowledgeBaseDbService knowledgeBaseDbService;
    @Mock
    private FileStorage fileStorage;

    private LineageQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        SecurityTestSupport.loginViewer();
        boundKnowledgeBase(10L);
        FileResultAccessGuard accessGuard = new FileResultAccessGuard(fileResultDbService, knowledgeBaseDbService);
        service = new LineageQueryServiceImpl(pipelineTaskDbService,
                pipelineProductDbService, stepLogDbService, chunkSetDbService, embeddingSetDbService,
                fileStorage, accessGuard, new ChunkStrategyParser(new ChunkProperties()));
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
        when(fileResultDbService.getById(10L)).thenReturn(fileResultOfKb10());
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
                product(10L, PipelineStage.PARSE.name(), "art-p",
                        "{\"parserName\":\"pdfbox\",\"parserVersion\":\"3.0.4\"}"),
                product(20L, PipelineStage.STRUCTURE.name(), "art-s",
                        "{\"parserName\":\"rules\",\"parserVersion\":\"v1\"}"),
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
        // 能力快照（无策略环节）：JSON 文本被解析成对象，而不是透传原文
        assertEquals("pdfbox", vo.getNodes().get(0).getCapability().getParserName());
        assertEquals("3.0.4", vo.getNodes().get(0).getCapability().getParserVersion());
        assertEquals("rules", vo.getNodes().get(1).getCapability().getParserName());
        assertEquals("v1", vo.getNodes().get(1).getCapability().getParserVersion());
        // 有策略环节不填能力快照
        assertNull(vo.getNodes().get(2).getCapability());
        // 统计摘要：计数按数字下发（前端要按数字格式化千分位）
        assertEquals(156, vo.getNodes().get(4).getStats().get("chunkCount"));
        assertEquals(782, vo.getNodes().get(6).getStats().get("recordCount"));
        assertEquals(12, vo.getNodes().get(6).getStats().get("cachedCount"));
        assertEquals(1200L, vo.getNodes().get(2).getStats().get("matched"));
        assertEquals(64L, vo.getNodes().get(2).getStats().get("changed"));
        // 血缘边：p→s, s→pr1, s→pr2, pr1→c1, pr2→c2, c1→e1 = 6 条
        assertEquals(6, vo.getEdges().size());
        assertEquals(20L, vo.getEdges().get(0).getFromTaskId());
        assertEquals(51L, vo.getEdges().get(0).getToTaskId());
    }

    @Test
    void shouldReturnEmptyForFileWithoutTasks() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResultOfKb10());
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
        when(fileResultDbService.getById(10L)).thenReturn(fileResultOfKb10());
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

    @Test
    void parseNodeShouldCarryProductStats() {
        KbPipelineTask parseTask = task(20L, PipelineStage.PARSE.name(), null, 10L, null);
        parseTask.setStatus(PipelineTaskStatus.SUCCESS.name());
        parseTask.setStartedAt(LocalDateTime.of(2024, 5, 1, 10, 0, 0));
        parseTask.setFinishedAt(LocalDateTime.of(2024, 5, 1, 10, 0, 8));
        when(fileResultDbService.getById(10L)).thenReturn(fileResultOfKb10());
        when(pipelineTaskDbService.listByFileResultId(10L)).thenReturn(List.of(parseTask));
        when(pipelineProductDbService.listByFileResultId(10L)).thenReturn(List.of(
                product(10L, PipelineStage.PARSE.name(), "art-p",
                        "{\"parserName\":\"pdfbox\",\"parserVersion\":\"3.0.4\"}")));
        when(chunkSetDbService.listByFileResultId(10L)).thenReturn(List.of());
        when(embeddingSetDbService.listByFileResultId(10L)).thenReturn(List.of());
        // 产物本体：2 段落 + 1 表格 + 2 图片 + 1 页眉 + 1 页脚 = 7 个元素；第 2 页未解析出内容
        ParseSource source = new ParseSource();
        source.setUnitCount(5);
        source.setElements(List.of(
                element(ElementType.PARAGRAPH), element(ElementType.PARAGRAPH),
                element(ElementType.TABLE), element(ElementType.IMAGE),
                element(ElementType.IMAGE), element(ElementType.HEADER),
                element(ElementType.FOOTER)));
        QualityInfo quality = new QualityInfo();
        quality.setFailedPages(List.of(2));
        ParseResult parseResult = new ParseResult();
        parseResult.setSources(List.of(source));
        parseResult.setQuality(quality);
        when(fileStorage.getObject("art-p"))
                .thenReturn(JsonUtil.toJsonStr(parseResult).getBytes(StandardCharsets.UTF_8));

        LineageVO vo = service.lineage(10L);

        LineageNodeVO node = vo.getNodes().get(0);
        assertEquals(5, node.getParseStats().getPageCount());
        assertEquals(7, node.getParseStats().getElementCount());
        assertEquals(2, node.getParseStats().getBodyCount());
        assertEquals(1, node.getParseStats().getTableCount());
        assertEquals(2, node.getParseStats().getImageCount());
        assertEquals(2, node.getParseStats().getHeaderFooterCount());
        assertEquals(1, node.getParseStats().getFailedUnitCount());
        assertEquals(2, node.getParseStats().getFailedFrom());
        assertEquals(2, node.getParseStats().getFailedTo());
        assertEquals(8000L, node.getParseStats().getDurationMs());
        assertEquals("1 单元未解析出内容（第 2–2）", node.getParseSummary());
    }

    @Test
    void parseNodeWithoutArtifactShouldFallBackToPageCount() {
        KbPipelineTask parseTask = task(20L, PipelineStage.PARSE.name(), null, 10L, null);
        parseTask.setStatus(PipelineTaskStatus.SUCCESS.name());
        parseTask.setStartedAt(LocalDateTime.of(2024, 5, 1, 10, 0, 0));
        parseTask.setFinishedAt(LocalDateTime.of(2024, 5, 1, 10, 0, 8));
        when(fileResultDbService.getById(10L)).thenReturn(fileResultOfKb10());
        when(pipelineTaskDbService.listByFileResultId(10L)).thenReturn(List.of(parseTask));
        when(pipelineProductDbService.listByFileResultId(10L)).thenReturn(List.of(
                product(10L, PipelineStage.PARSE.name(), "art-p",
                        "{\"parserName\":\"pdfbox\",\"parserVersion\":\"3.0.4\"}")));
        when(chunkSetDbService.listByFileResultId(10L)).thenReturn(List.of());
        when(embeddingSetDbService.listByFileResultId(10L)).thenReturn(List.of());
        // 产物本体：无判定单元数，页数取文件引用；无问题单元 → 成功态摘要为"无异常"
        ParseSource source = new ParseSource();
        source.setElements(List.of(element(ElementType.PARAGRAPH)));
        ParseResult parseResult = new ParseResult();
        parseResult.setSources(List.of(source));
        parseResult.setQuality(new QualityInfo());
        parseResult.setFile(new FileReference("f-1", "a.pdf", "sha", "application/pdf", 128));
        when(fileStorage.getObject("art-p"))
                .thenReturn(JsonUtil.toJsonStr(parseResult).getBytes(StandardCharsets.UTF_8));

        LineageVO vo = service.lineage(10L);

        LineageNodeVO node = vo.getNodes().get(0);
        assertEquals(128, node.getParseStats().getPageCount());
        assertEquals(1, node.getParseStats().getElementCount());
        assertNull(node.getParseStats().getFailedUnitCount());
        assertNull(node.getParseStats().getFailedFrom());
        assertNull(node.getParseStats().getFailedTo());
        assertEquals("无异常", node.getParseSummary());
    }

    /**
     * 产物不可读：统计与摘要都不下发 —— 统计没到手时不得断言"无异常"。
     *
     * <p>成功与部分成功两种终态都覆盖：两者的摘要判据同源（统计里的问题单元）。
     */
    @ParameterizedTest
    @EnumSource(value = PipelineTaskStatus.class, names = { "SUCCESS", "PARTIAL_SUCCESS" })
    void unreadableArtifactShouldLeaveParseStatsAndSummaryEmpty(PipelineTaskStatus status) {
        KbPipelineTask parseTask = task(20L, PipelineStage.PARSE.name(), null, 10L, null);
        parseTask.setStatus(status.name());
        parseTask.setStartedAt(LocalDateTime.of(2024, 5, 1, 10, 0, 0));
        parseTask.setFinishedAt(LocalDateTime.of(2024, 5, 1, 10, 0, 8));
        when(fileResultDbService.getById(10L)).thenReturn(fileResultOfKb10());
        when(pipelineTaskDbService.listByFileResultId(10L)).thenReturn(List.of(parseTask));
        when(pipelineProductDbService.listByFileResultId(10L)).thenReturn(List.of(
                product(10L, PipelineStage.PARSE.name(), "art-p",
                        "{\"parserName\":\"pdfbox\",\"parserVersion\":\"3.0.4\"}")));
        when(chunkSetDbService.listByFileResultId(10L)).thenReturn(List.of());
        when(embeddingSetDbService.listByFileResultId(10L)).thenReturn(List.of());
        when(fileStorage.getObject("art-p"))
                .thenThrow(new KnowledgeException(ErrorCode.FILE_NOT_FOUND, "对象读取失败"));

        LineageVO vo = service.lineage(10L);

        LineageNodeVO node = vo.getNodes().get(0);
        assertAll(
                () -> assertNull(node.getParseStats(), status + ": 产物不可读时不下发统计"),
                () -> assertNull(node.getParseSummary(), status + ": 产物不可读时不下发摘要"));
    }

    /**
     * 未完成（QUEUED / RUNNING）且尚无产物的任务同样是可见节点：节点=一次运行，
     * 卡片按状态显示"排队中/解析中"，统计与摘要留空。
     */
    @ParameterizedTest
    @EnumSource(value = PipelineTaskStatus.class, names = { "QUEUED", "RUNNING" })
    void pendingParseTaskWithoutProductShouldBeVisible(PipelineTaskStatus status) {
        KbPipelineTask parseTask = task(20L, PipelineStage.PARSE.name(), null, null, null);
        parseTask.setStatus(status.name());
        when(fileResultDbService.getById(10L)).thenReturn(fileResultOfKb10());
        when(pipelineTaskDbService.listByFileResultId(10L)).thenReturn(List.of(parseTask));
        when(pipelineProductDbService.listByFileResultId(10L)).thenReturn(List.of());
        when(chunkSetDbService.listByFileResultId(10L)).thenReturn(List.of());
        when(embeddingSetDbService.listByFileResultId(10L)).thenReturn(List.of());

        LineageVO vo = service.lineage(10L);

        assertEquals(1, vo.getNodes().size(), status + ": 未完成任务必须在血缘里可见");
        LineageNodeVO node = vo.getNodes().get(0);
        assertEquals(status.name(), node.getStatus());
        assertEquals(PipelineStage.PARSE.name(), node.getStage());
        assertNull(node.getProductId());
        assertNull(node.getParseStats());
        assertNull(node.getParseSummary());
    }

    /** 产物里的一个元素（只有类型参与汇总） */
    private ParseElement element(ElementType type) {
        return ParseElement.of("e-" + type.name(), type);
    }
    @Test
    void otherUserFileResultShouldReject40401() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResultOfKb10());
        // 归属看的是知识库归属：换一个登录用户，越权与"不存在"同样返回 40401
        SecurityTestSupport.loginOtherUser();

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.lineage(10L));
        assertEquals(ErrorCode.KB_NOT_FOUND, e.getErrorCode());
    }


    /** 让指定知识库归当前登录用户所有（归属校验要能过） */
    private void boundKnowledgeBase(Long id) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(id);
        kb.setUserId(SecurityTestSupport.VIEWER_ID);
        lenient().when(knowledgeBaseDbService.getActiveById(id)).thenReturn(kb);
    }

    /** 一次运行的文件结果（挂 10 号知识库，归属校验要能过） */
    private KbFileResult fileResultOfKb10() {
        KbFileResult fileResult = new KbFileResult();
        fileResult.setId(10L);
        fileResult.setKnowledgeBaseId(10L);
        return fileResult;
    }
}