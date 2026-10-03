package com.knowledge.biz.service.impl;

import com.knowledge.biz.service.StageContentQueryService;
import com.knowledge.biz.service.db.KbChunkDbService;
import com.knowledge.biz.service.db.KbChunkSetDbService;
import com.knowledge.biz.service.db.KbEmbeddingRecordDbService;
import com.knowledge.biz.service.db.KbEmbeddingSetDbService;
import com.knowledge.biz.service.db.KnowledgeBaseDbService;
import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineStepLogDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.biz.service.support.FileResultAccessGuard;
import com.knowledge.biz.service.support.TaskDetailSupport;
import com.knowledge.biz.testkit.SecurityTestSupport;
import com.knowledge.common.domain.entity.KnowledgeBase;
import com.knowledge.common.domain.entity.KbChunk;
import com.knowledge.common.domain.entity.KbChunkSet;
import com.knowledge.common.domain.entity.KbEmbeddingRecord;
import com.knowledge.common.domain.entity.KbEmbeddingSet;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.parse.BBox;
import com.knowledge.common.domain.parse.ParseElement;
import com.knowledge.common.domain.parse.ParseResult;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.domain.preprocess.PreprocessView;
import com.knowledge.common.domain.preprocess.ViewElement;
import com.knowledge.common.dto.response.stagecontent.StageContentVO;
import com.knowledge.common.enums.parse.ElementType;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.filecenter.service.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 产物内容查询单测：白名单/历史运行精确取产物/预处理内容映射/切片与向量化列表。
 *
 * @author cxxl
 */
@ExtendWith(MockitoExtension.class)
class StageContentQueryServiceImplTest {

    @Mock
    private KbPipelineTaskDbService pipelineTaskDbService;
    @Mock
    private KbPipelineProductDbService pipelineProductDbService;
    @Mock
    private KbPipelineStepLogDbService stepLogDbService;
    @Mock
    private KbChunkSetDbService chunkSetDbService;
    @Mock
    private KbChunkDbService chunkDbService;
    @Mock
    private KbEmbeddingSetDbService embeddingSetDbService;
    @Mock
    private KbEmbeddingRecordDbService embeddingRecordDbService;
    @Mock
    private KbFileResultDbService fileResultDbService;
    @Mock
    private KnowledgeBaseDbService knowledgeBaseDbService;
    @Mock
    private FileStorage fileStorage;

    private StageContentQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        SecurityTestSupport.loginViewer();
        boundKnowledgeBase(10L);
        FileResultAccessGuard accessGuard = new FileResultAccessGuard(fileResultDbService, knowledgeBaseDbService);
        service = new StageContentQueryServiceImpl(fileResultDbService, pipelineProductDbService,
                chunkSetDbService, chunkDbService, embeddingSetDbService, embeddingRecordDbService,
                fileStorage, new TaskDetailSupport(pipelineTaskDbService, stepLogDbService, pipelineProductDbService), accessGuard);
    }

    private KbPipelineTask latestTask(String stage) {
        KbPipelineTask task = new KbPipelineTask();
        task.setId(41L);
        task.setFileResultId(10L);
        task.setStage(stage);
        task.setStatus(PipelineTaskStatus.SUCCESS.name());
        task.setProductId(50L);
        return task;
    }

    @Test
    void illegalStageShouldReject40001() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResultOfKb10());

        KnowledgeException e = assertThrows(KnowledgeException.class,
                () -> service.stageContent(10L, "BUILD_INDEX", null, null, null, null));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
    }

    @Test
    void historyTaskShouldReturnOwnContent() {
        // 历史任务按 task.productId 精确取自己的产物内容（不再仅最新任务可展示）
        when(fileResultDbService.getById(10L)).thenReturn(fileResultOfKb10());
        KbPipelineTask task = latestTask(PipelineStage.CHUNK.name());
        when(pipelineTaskDbService.getById(41L)).thenReturn(task);
        KbPipelineProduct product = new KbPipelineProduct();
        product.setArtifactId("art-c1");
        when(pipelineProductDbService.getById(50L)).thenReturn(product);
        KbChunkSet chunkSet = new KbChunkSet();
        chunkSet.setId(70L);
        when(chunkSetDbService.getByArtifactId("art-c1")).thenReturn(chunkSet);
        KbChunk chunk = new KbChunk();
        chunk.setChunkId("chunk-0001");
        chunk.setContent("正文片");
        chunk.setContentType("PARAGRAPH");
        when(chunkDbService.listByChunkSetId(70L)).thenReturn(List.of(chunk));

        StageContentVO vo = service.stageContent(10L, "CHUNK", 41L, null, null, null);

        assertTrue(vo.getLatest());
        assertEquals(1, vo.getItems().size());
        assertEquals("正文片", vo.getItems().get(0).getDisplay());
    }

    @Test
    void preprocessShouldMapDisplayNormalizedAndStatus() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResultOfKb10());
        KbPipelineTask task = latestTask(PipelineStage.PREPROCESS.name());
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.PREPROCESS.name()))
                .thenReturn(task);
        KbPipelineProduct product = new KbPipelineProduct();
        product.setArtifactId("art-p1");
        when(pipelineProductDbService.getById(50L)).thenReturn(product);
        PreprocessView view = new PreprocessView();
        ViewElement kept = new ViewElement();
        kept.setElementId("el-1");
        kept.setType("PARAGRAPH");
        kept.setStatus("KEPT");
        kept.setDisplayText("投标总价 壹仟贰佰");
        kept.setNormalizedText("投标总价 1200");
        ViewElement excluded = new ViewElement();
        excluded.setElementId("el-2");
        excluded.setType("HEADER");
        excluded.setStatus("EXCLUDED_HEADER");
        excluded.setDisplayText("第 1 页 共 10 页");
        excluded.setNormalizedText(null);
        view.setElements(List.of(kept, excluded));
        when(fileStorage.getObject("art-p1"))
                .thenReturn(Objects.requireNonNull(JsonUtil.toJsonStr(view)).getBytes(StandardCharsets.UTF_8));

        StageContentVO vo = service.stageContent(10L, "PREPROCESS", null, null, null, null);

        assertTrue(vo.getLatest());
        assertEquals(2, vo.getItems().size());
        assertEquals("el-1", vo.getItems().get(0).getAlignKey());
        assertEquals("投标总价 壹仟贰佰", vo.getItems().get(0).getDisplay());
        assertEquals("投标总价 1200", vo.getItems().get(0).getNormalized());
        assertEquals("KEPT", vo.getItems().get(0).getStatus());
        assertNull(vo.getItems().get(1).getNormalized());
        assertEquals("EXCLUDED_HEADER", vo.getItems().get(1).getStatus());
    }

    @Test
    void chunkShouldMapOrderAlignedItems() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResultOfKb10());
        KbPipelineTask task = latestTask(PipelineStage.CHUNK.name());
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.CHUNK.name()))
                .thenReturn(task);
        KbPipelineProduct product = new KbPipelineProduct();
        product.setArtifactId("art-c1");
        when(pipelineProductDbService.getById(50L)).thenReturn(product);
        KbChunkSet chunkSet = new KbChunkSet();
        chunkSet.setId(70L);
        when(chunkSetDbService.getByArtifactId("art-c1")).thenReturn(chunkSet);
        KbChunk chunk = new KbChunk();
        chunk.setChunkId("chunk-0001");
        chunk.setContent("正文片");
        chunk.setContentType("PARAGRAPH");
        chunk.setTitlePath("第一章");
        chunk.setCharCount(3);
        chunk.setTokenCount(2);
        when(chunkDbService.listByChunkSetId(70L)).thenReturn(List.of(chunk));

        StageContentVO vo = service.stageContent(10L, "CHUNK", null, null, null, null);

        assertEquals(1, vo.getItems().size());
        assertEquals("1", vo.getItems().get(0).getAlignKey());
        assertEquals("正文片", vo.getItems().get(0).getDisplay());
        assertEquals("第一章", vo.getItems().get(0).getExtra().get("titlePath"));
    }

    @Test
    void embedShouldMapRecordsWithCacheHit() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResultOfKb10());
        KbPipelineTask task = latestTask(PipelineStage.EMBED.name());
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.EMBED.name()))
                .thenReturn(task);
        KbPipelineProduct product = new KbPipelineProduct();
        product.setArtifactId("art-e1");
        when(pipelineProductDbService.getById(50L)).thenReturn(product);
        KbEmbeddingSet set = new KbEmbeddingSet();
        set.setId(90L);
        when(embeddingSetDbService.getByArtifactId("art-e1")).thenReturn(set);
        KbEmbeddingRecord cached = new KbEmbeddingRecord();
        cached.setChunkId("chunk-0001");
        cached.setContentType("PARAGRAPH");
        cached.setInputText("正文片");
        cached.setStatus("CACHED");
        cached.setCacheHit(true);
        cached.setTokenCount(2);
        when(embeddingRecordDbService.listByEmbeddingSetId(90L)).thenReturn(List.of(cached));

        StageContentVO vo = service.stageContent(10L, "EMBED", null, null, null, null);

        assertEquals(1, vo.getItems().size());
        assertEquals("CACHED", vo.getItems().get(0).getStatus());
        assertEquals(Boolean.TRUE, vo.getItems().get(0).getExtra().get("cacheHit"));
    }

    @Test
    void stageContentWithoutTaskShouldReturnLatestFalse() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResultOfKb10());
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.CHUNK.name()))
                .thenReturn(null);

        StageContentVO vo = service.stageContent(10L, "CHUNK", null, null, null, null);

        assertFalse(vo.getLatest());
        assertTrue(vo.getItems().isEmpty());
        assertNull(vo.getTaskId());
    }

    @Test
    void missingFileResultShouldReject40432() {
        when(fileResultDbService.getById(10L)).thenReturn(null);

        KnowledgeException e = assertThrows(KnowledgeException.class,
                () -> service.stageContent(10L, "CHUNK", null, null, null, null));
        assertEquals(ErrorCode.FILE_RESULT_NOT_FOUND, e.getErrorCode());
    }

    @Test
    void taskIdOfWrongStageShouldReject40001() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResultOfKb10());
        KbPipelineTask preprocessTask = new KbPipelineTask();
        preprocessTask.setId(41L);
        preprocessTask.setFileResultId(10L);
        preprocessTask.setStage(PipelineStage.PREPROCESS.name());
        when(pipelineTaskDbService.getById(41L)).thenReturn(preprocessTask);

        KnowledgeException e = assertThrows(KnowledgeException.class,
                () -> service.stageContent(10L, "CHUNK", 41L, null, null, null));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
    }

    /** 造一份含 elementCount 个元素的解析产物，并让 41 号 PARSE 任务取到它 */
    private void stubParseArtifact(int elementCount) {
        when(fileResultDbService.getById(10L)).thenReturn(fileResultOfKb10());
        KbPipelineTask task = latestTask(PipelineStage.PARSE.name());
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.PARSE.name()))
                .thenReturn(task);
        KbPipelineProduct product = new KbPipelineProduct();
        product.setArtifactId("art-parse");
        when(pipelineProductDbService.getById(50L)).thenReturn(product);
        ParseSource source = new ParseSource();
        source.setSource("native");
        source.setProvider("pdfbox-3.0.4");
        List<ParseElement> elements = new ArrayList<>();
        for (int i = 1; i <= elementCount; i++) {
            ParseElement element = ParseElement.of("el-" + i, ElementType.PARAGRAPH);
            element.setPage(i);
            elements.add(element);
        }
        source.setElements(elements);
        ParseResult result = new ParseResult();
        result.setSources(List.of(source));
        when(fileStorage.getObject("art-parse"))
                .thenReturn(Objects.requireNonNull(JsonUtil.toJsonStr(result)).getBytes(StandardCharsets.UTF_8));
    }

    /** 造一份带坐标的解析产物：每页一个元素，坐标随页号变化 */
    private void stubParseArtifactWithBbox(int elementCount) {
        when(fileResultDbService.getById(10L)).thenReturn(fileResultOfKb10());
        KbPipelineTask task = latestTask(PipelineStage.PARSE.name());
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.PARSE.name()))
                .thenReturn(task);
        KbPipelineProduct product = new KbPipelineProduct();
        product.setArtifactId("art-parse");
        when(pipelineProductDbService.getById(50L)).thenReturn(product);
        ParseSource source = new ParseSource();
        source.setSource("native");
        source.setProvider("pdfbox-3.0.4");
        List<ParseElement> elements = new ArrayList<>();
        for (int i = 1; i <= elementCount; i++) {
            ParseElement element = ParseElement.of("el-" + i, ElementType.PARAGRAPH);
            element.setPage(i);
            element.setBbox(new BBox(72, 100 * i, 400, 20));
            elements.add(element);
        }
        source.setElements(elements);
        ParseResult result = new ParseResult();
        result.setSources(List.of(source));
        when(fileStorage.getObject("art-parse"))
                .thenReturn(Objects.requireNonNull(JsonUtil.toJsonStr(result)).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parseExtraShouldCarryBbox() {
        stubParseArtifactWithBbox(2);

        StageContentVO vo = service.stageContent(10L, "PARSE", null, null, null, null);

        Map<String, Object> first = vo.getItems().get(0).getExtra();
        assertEquals(1, first.get("page"));
        @SuppressWarnings("unchecked")
        Map<String, Object> bbox = (Map<String, Object>) first.get("bbox");
        assertNotNull(bbox);
        assertEquals(72.0, bbox.get("x"));
        assertEquals(100.0, bbox.get("y"));
        assertEquals(400.0, bbox.get("width"));
        assertEquals(20.0, bbox.get("height"));
    }

    @Test
    void elementWithoutBboxShouldNotCarryTheField() {
        stubParseArtifact(2);

        StageContentVO vo = service.stageContent(10L, "PARSE", null, null, null, null);

        assertFalse(vo.getItems().get(0).getExtra().containsKey("bbox"));
    }

    @Test
    void docPageShouldNarrowItemsAndTotal() {
        stubParseArtifactWithBbox(5);

        StageContentVO vo = service.stageContent(10L, "PARSE", null, 3L, null, null);

        assertEquals(3, vo.getDocPage());
        assertEquals(1, vo.getTotal());
        assertEquals(1, vo.getItems().size());
        assertEquals("el-3", vo.getItems().get(0).getAlignKey());
        assertFalse(vo.getTruncated());

        // 不传 docPage：total 回到全量
        StageContentVO all = service.stageContent(10L, "PARSE", null, null, null, null);
        assertNull(all.getDocPage());
        assertEquals(5, all.getTotal());
    }

    @Test
    void docPageWithoutMatchShouldReturnEmpty() {
        stubParseArtifactWithBbox(2);

        StageContentVO vo = service.stageContent(10L, "PARSE", null, 9L, null, null);

        assertEquals(9, vo.getDocPage());
        assertEquals(0, vo.getTotal());
        assertTrue(vo.getItems().isEmpty());
    }

    @Test
    void pageAndLimitShouldSliceItemsAndReportTotal() {
        stubParseArtifact(5);

        StageContentVO first = service.stageContent(10L, "PARSE", null, null, 1, 2);
        assertEquals(5, first.getTotal());
        assertEquals(2, first.getItems().size());
        assertEquals("el-1", first.getItems().get(0).getAlignKey());
        assertEquals(1, first.getPage());
        assertEquals(2, first.getLimit());
        assertTrue(first.getTruncated());

        StageContentVO last = service.stageContent(10L, "PARSE", null, null, 3, 2);
        assertEquals(5, last.getTotal());
        assertEquals(1, last.getItems().size());
        assertEquals("el-5", last.getItems().get(0).getAlignKey());
        assertFalse( last.getTruncated());
    }

    @Test
    void pagingDefaultsShouldReturnFirstPageWithinLimit() {
        stubParseArtifact(5);

        // 不传分页参数：按第 1 页 + 默认上限返回，内容与全量一致（向后兼容）
        StageContentVO vo = service.stageContent(10L, "PARSE", null, null, null, null);

        assertEquals(5, vo.getTotal());
        assertEquals(5, vo.getItems().size());
        assertEquals(1, vo.getPage());
        assertEquals(StageContentQueryService.DEFAULT_LIMIT, vo.getLimit());
        assertFalse(vo.getTruncated());
    }

    @Test
    void limitAboveMaxShouldBeCappedAndOverflowPageShouldBeEmpty() {
        stubParseArtifact(5);

        StageContentVO capped = service.stageContent(10L, "PARSE", null, null, 1, StageContentQueryService.MAX_LIMIT + 500);
        assertEquals(StageContentQueryService.MAX_LIMIT, capped.getLimit());
        assertEquals(5, capped.getItems().size());

        // 页码越界：内容为空但 total 仍是真实总数；已取到总数之后，不再算"还有内容"
        StageContentVO overflow = service.stageContent(10L, "PARSE", null, null, 99, 2);
        assertTrue(overflow.getItems().isEmpty());
        assertEquals(5, overflow.getTotal());
        assertFalse(overflow.getTruncated());
    }

    @Test
    void lastPartialPageShouldNotBeReportedAsTruncated() {
        stubParseArtifact(5);

        // 5 条按每页 2 条取第 3 页：只回 1 条，但已经是最后一页 —— 不能报还有内容
        StageContentVO third = service.stageContent(10L, "PARSE", null, null, 3, 2);
        assertEquals(1, third.getItems().size());
        assertEquals(5, third.getTotal());
        assertFalse(third.getTruncated());

        // 第 2 页取满且后面还有 1 条 —— 要报还有内容
        StageContentVO second = service.stageContent(10L, "PARSE", null, null, 2, 2);
        assertEquals(2, second.getItems().size());
        assertTrue(second.getTruncated());
    }    @Test
    void otherUserFileResultShouldReject40401() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResultOfKb10());
        // 归属看的是知识库归属：换一个登录用户，越权与"不存在"同样返回 40401
        SecurityTestSupport.loginOtherUser();

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.stageContent(10L, "PARSE", null, null, null, null));
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
