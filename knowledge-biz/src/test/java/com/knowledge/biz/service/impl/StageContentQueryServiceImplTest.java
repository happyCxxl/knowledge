package com.knowledge.biz.service.impl;

import com.knowledge.biz.service.db.KbChunkDbService;
import com.knowledge.biz.service.db.KbChunkSetDbService;
import com.knowledge.biz.service.db.KbEmbeddingRecordDbService;
import com.knowledge.biz.service.db.KbEmbeddingSetDbService;
import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.biz.service.support.TaskDetailSupport;
import com.knowledge.common.domain.entity.KbChunk;
import com.knowledge.common.domain.entity.KbChunkSet;
import com.knowledge.common.domain.entity.KbEmbeddingRecord;
import com.knowledge.common.domain.entity.KbEmbeddingSet;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.preprocess.PreprocessView;
import com.knowledge.common.domain.preprocess.ViewElement;
import com.knowledge.common.dto.response.stagecontent.StageContentVO;
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
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private FileStorage fileStorage;

    private StageContentQueryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StageContentQueryServiceImpl(fileResultDbService, pipelineProductDbService,
                chunkSetDbService, chunkDbService, embeddingSetDbService, embeddingRecordDbService,
                fileStorage, new TaskDetailSupport(pipelineTaskDbService));
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
        when(fileResultDbService.getById(10L)).thenReturn(new KbFileResult());

        KnowledgeException e = assertThrows(KnowledgeException.class,
                () -> service.stageContent(10L, "BUILD_INDEX", null));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
    }

    @Test
    void historyTaskShouldReturnOwnContent() {
        // step-13 口径修正：历史任务按 task.productId 精确取自己的产物内容（不再仅最新任务可展示）
        when(fileResultDbService.getById(10L)).thenReturn(new KbFileResult());
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

        StageContentVO vo = service.stageContent(10L, "CHUNK", 41L);

        assertTrue(vo.getLatest());
        assertEquals(1, vo.getItems().size());
        assertEquals("正文片", vo.getItems().get(0).getDisplay());
    }

    @Test
    void preprocessShouldMapDisplayNormalizedAndStatus() {
        when(fileResultDbService.getById(10L)).thenReturn(new KbFileResult());
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

        StageContentVO vo = service.stageContent(10L, "PREPROCESS", null);

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
        when(fileResultDbService.getById(10L)).thenReturn(new KbFileResult());
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

        StageContentVO vo = service.stageContent(10L, "CHUNK", null);

        assertEquals(1, vo.getItems().size());
        assertEquals("1", vo.getItems().get(0).getAlignKey());
        assertEquals("正文片", vo.getItems().get(0).getDisplay());
        assertEquals("第一章", vo.getItems().get(0).getExtra().get("titlePath"));
    }

    @Test
    void embedShouldMapRecordsWithCacheHit() {
        when(fileResultDbService.getById(10L)).thenReturn(new KbFileResult());
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

        StageContentVO vo = service.stageContent(10L, "EMBED", null);

        assertEquals(1, vo.getItems().size());
        assertEquals("CACHED", vo.getItems().get(0).getStatus());
        assertEquals(Boolean.TRUE, vo.getItems().get(0).getExtra().get("cacheHit"));
    }

    @Test
    void stageContentWithoutTaskShouldReturnLatestFalse() {
        when(fileResultDbService.getById(10L)).thenReturn(new KbFileResult());
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.CHUNK.name()))
                .thenReturn(null);

        StageContentVO vo = service.stageContent(10L, "CHUNK", null);

        assertFalse(vo.getLatest());
        assertTrue(vo.getItems().isEmpty());
        assertNull(vo.getTaskId());
    }

    @Test
    void missingFileResultShouldReject40432() {
        when(fileResultDbService.getById(10L)).thenReturn(null);

        KnowledgeException e = assertThrows(KnowledgeException.class,
                () -> service.stageContent(10L, "CHUNK", null));
        assertEquals(ErrorCode.FILE_RESULT_NOT_FOUND, e.getErrorCode());
    }

    @Test
    void taskIdOfWrongStageShouldReject40001() {
        when(fileResultDbService.getById(10L)).thenReturn(new KbFileResult());
        KbPipelineTask preprocessTask = new KbPipelineTask();
        preprocessTask.setId(41L);
        preprocessTask.setFileResultId(10L);
        preprocessTask.setStage(PipelineStage.PREPROCESS.name());
        when(pipelineTaskDbService.getById(41L)).thenReturn(preprocessTask);

        KnowledgeException e = assertThrows(KnowledgeException.class,
                () -> service.stageContent(10L, "CHUNK", 41L));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
    }
}
