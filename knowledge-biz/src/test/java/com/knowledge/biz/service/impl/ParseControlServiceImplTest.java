package com.knowledge.biz.service.impl;

import com.knowledge.biz.service.db.KnowledgeBaseDbService;
import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineStepLogDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.biz.service.db.KbSourceFileDbService;
import com.knowledge.biz.service.support.ChainStorageSupport;
import com.knowledge.biz.service.support.FileResultAccessGuard;
import com.knowledge.biz.service.support.TaskDetailSupport;
import com.knowledge.biz.task.TaskQueueSupport;
import com.knowledge.biz.task.TaskTriggerSupport;
import com.knowledge.biz.testkit.SecurityTestSupport;
import com.knowledge.common.domain.entity.KnowledgeBase;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineStepLog;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.storage.ObjectRef;
import com.knowledge.common.dto.response.parse.ParseDetailVO;
import com.knowledge.common.enums.storage.StorageType;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import com.knowledge.filecenter.provider.StorageRouter;
import com.knowledge.filecenter.service.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 解析控制面服务单测：触发防重/建任务入队、解析详情组装。
 *
 * @author cxxl
 */
@ExtendWith(MockitoExtension.class)
class ParseControlServiceImplTest {

    /** 产物所在的数据源 */
    private static final Long SOURCE_ID = 1L;

    @Mock
    private KbFileResultDbService fileResultDbService;
    @Mock
    private KbPipelineTaskDbService pipelineTaskDbService;
    @Mock
    private KbPipelineProductDbService pipelineProductDbService;
    @Mock
    private KbPipelineStepLogDbService stepLogDbService;
    @Mock
    private TaskQueueSupport taskQueue;
    @Mock
    private KnowledgeBaseDbService knowledgeBaseDbService;
    @Mock
    private KbSourceFileDbService sourceFileDbService;
    @Mock
    private StorageRouter storageRouter;
    @Mock
    private FileStorage fileStorage;

    private ParseControlServiceImpl service;

    @BeforeEach
    void setUp() {
        // 触发/详情助手为纯委托类，用真实实例（mock 会让返回失真）
        SecurityTestSupport.loginViewer();
        boundKnowledgeBase(10L);
        FileResultAccessGuard accessGuard = new FileResultAccessGuard(fileResultDbService, knowledgeBaseDbService);
        ChainStorageSupport chainStorage = new ChainStorageSupport(fileResultDbService, sourceFileDbService,
                fileStorage, storageRouter);
        service = new ParseControlServiceImpl(pipelineProductDbService,
                new TaskTriggerSupport(pipelineTaskDbService, chainStorage, taskQueue),
                new TaskDetailSupport(pipelineTaskDbService, stepLogDbService, pipelineProductDbService), fileStorage,
                accessGuard);
    }

    private KbFileResult fileResult() {
        KbFileResult fileResult = new KbFileResult();
        fileResult.setId(10L);
        fileResult.setKnowledgeBaseId(10L);
        return fileResult;
    }

    @Test
    void parseShouldCreateTaskAndEnqueue() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.PARSE.name()))
                .thenReturn(null);
        when(pipelineTaskDbService.save(any(KbPipelineTask.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbPipelineTask.class).setId(21L);
            return true;
        });

        var response = service.parse(10L);

        assertEquals(21L, response.getPipelineTaskId());
        ArgumentCaptor<KbPipelineTask> captor = ArgumentCaptor.forClass(KbPipelineTask.class);
        verify(pipelineTaskDbService).save(captor.capture());
        assertEquals(PipelineTaskStatus.QUEUED.name(), captor.getValue().getStatus());
        assertEquals(PipelineStage.PARSE.name(), captor.getValue().getStage());
        verify(taskQueue).enqueue(21L);
    }

    @Test
    void parseRunningShouldReject() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        KbPipelineTask running = new KbPipelineTask();
        running.setId(21L);
        running.setStatus(PipelineTaskStatus.RUNNING.name());
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.PARSE.name()))
                .thenReturn(running);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.parse(10L));
        assertEquals(ErrorCode.TASK_ALREADY_PENDING, e.getErrorCode());
        verify(pipelineTaskDbService, never()).save(any());
        verify(taskQueue, never()).enqueue(any());
    }

    @Test
    void parseQueuedShouldEnqueueExistingWithoutNewTask() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        KbPipelineTask queued = new KbPipelineTask();
        queued.setId(21L);
        queued.setStatus(PipelineTaskStatus.QUEUED.name());
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.PARSE.name()))
                .thenReturn(queued);

        var response = service.parse(10L);

        assertEquals(21L, response.getPipelineTaskId());
        verify(pipelineTaskDbService, never()).save(any());
        verify(taskQueue).enqueue(21L);
    }

    @Test
    void parseSuccessShouldReject() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        KbPipelineTask success = new KbPipelineTask();
        success.setId(21L);
        success.setStatus(PipelineTaskStatus.SUCCESS.name());
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.PARSE.name()))
                .thenReturn(success);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.parse(10L));
        assertEquals(ErrorCode.PARSE_ALREADY_SUCCEEDED, e.getErrorCode());
        verify(pipelineTaskDbService, never()).save(any());
        verify(taskQueue, never()).enqueue(any());
    }

    @Test
    void parsePartialSuccessShouldReject() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        KbPipelineTask partial = new KbPipelineTask();
        partial.setId(21L);
        partial.setStatus(PipelineTaskStatus.PARTIAL_SUCCESS.name());
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.PARSE.name()))
                .thenReturn(partial);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.parse(10L));
        assertEquals(ErrorCode.PARSE_ALREADY_SUCCEEDED, e.getErrorCode());
        verify(pipelineTaskDbService, never()).save(any());
    }

    @Test
    void parseFailedShouldCreateNewTask() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        KbPipelineTask failed = new KbPipelineTask();
        failed.setId(21L);
        failed.setStatus(PipelineTaskStatus.FAILED.name());
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.PARSE.name()))
                .thenReturn(failed);
        when(pipelineTaskDbService.save(any(KbPipelineTask.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbPipelineTask.class).setId(22L);
            return true;
        });

        var response = service.parse(10L);

        assertEquals(22L, response.getPipelineTaskId());
        verify(taskQueue).enqueue(22L);
    }

    @Test
    void parseMissingFileResultShouldReject() {
        when(fileResultDbService.getById(10L)).thenReturn(null);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.parse(10L));
        assertEquals(ErrorCode.FILE_RESULT_NOT_FOUND, e.getErrorCode());
    }

    @Test
    void detailShouldAssembleTaskStepsAndWarnings() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        KbPipelineTask task = new KbPipelineTask();
        task.setId(20L);
        task.setStage(PipelineStage.PARSE.name());
        task.setStatus(PipelineTaskStatus.PARTIAL_SUCCESS.name());
        task.setStartedAt(LocalDateTime.now());
        task.setProductId(50L);
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.PARSE.name())).thenReturn(task);
        KbPipelineStepLog step = new KbPipelineStepLog();
        step.setStepName("原生解析");
        step.setStatus("SUCCESS");
        step.setWarningCount(2);
        step.setMatchedCount(7);
        step.setChangedCount(3);
        step.setAvgLen(512);
        when(stepLogDbService.listByTaskId(20L)).thenReturn(List.of(step));
        KbPipelineProduct product = new KbPipelineProduct();
        product.setArtifactId("9f2c".repeat(16));
        product.setContentHash("9f2c".repeat(16));
        // 产物行记下对象位置：详情读取按数据源与对象键定位
        product.setStorageType(StorageType.MINIO.getCode());
        product.setStorageSourceId(SOURCE_ID);
        product.setBucket("artifacts");
        when(pipelineProductDbService.getById(50L)).thenReturn(product);
        String json = "{\"quality\":{\"warnings\":[{\"code\":\"SCANNED_PAGE\",\"level\":\"WARN\","
                + "\"message\":\"第 page 2 无文本层（扫描页），OCR 暂未支持\"}]}}";
        when(fileStorage.getObject(ObjectRef.of(SOURCE_ID, StorageType.MINIO.getCode(), "artifacts", "9f2c".repeat(16))))
                .thenReturn(json.getBytes(StandardCharsets.UTF_8));

        ParseDetailVO detail = service.parseDetail(10L, null);

        assertEquals(20L, detail.getTaskId());
        assertEquals(PipelineTaskStatus.PARTIAL_SUCCESS.name(), detail.getStatus());
        assertEquals(1, detail.getSteps().size());
        assertEquals(2, detail.getSteps().getFirst().getWarningCount());
        assertEquals(7, detail.getSteps().getFirst().getMatchedCount());
        assertEquals(3, detail.getSteps().getFirst().getChangedCount());
        assertEquals(512, detail.getSteps().getFirst().getAvgLen());
        assertEquals(1, detail.getWarnings().size());
        assertTrue(detail.getWarnings().getFirst().contains("SCANNED_PAGE"));
    }

    @Test
    void detailWithIncompleteProductLocationShouldReject40455() {
        KbPipelineTask task = new KbPipelineTask();
        task.setId(20L);
        task.setFileResultId(10L);
        task.setStage(PipelineStage.PARSE.name());
        task.setStatus(PipelineTaskStatus.SUCCESS.name());
        task.setProductId(50L);
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.PARSE.name())).thenReturn(task);
        // 产物行缺 storageType / storageSourceId / bucket：位置不完整，读取路径按「存储后端未配置」拒绝
        KbPipelineProduct product = new KbPipelineProduct();
        product.setArtifactId("9f2c".repeat(16));
        when(pipelineProductDbService.getById(50L)).thenReturn(product);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.parseDetail(10L, null));

        assertEquals(ErrorCode.STORAGE_BACKEND_UNCONFIGURED, e.getErrorCode());
        verify(fileStorage, never()).getObject(any(ObjectRef.class));
    }

    @Test
    void detailWithoutProductShouldReturnEmptyWarnings() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.PARSE.name())).thenReturn(null);

        ParseDetailVO detail = service.parseDetail(10L, null);

        assertNotNull(detail.getWarnings());
        assertTrue(detail.getWarnings().isEmpty());
    }

    @Test
    void otherUserFileResultShouldReject40401() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        // 归属看的是知识库归属：换一个登录用户，越权与"不存在"同样返回 40401
        SecurityTestSupport.loginOtherUser();

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.parse(10L));
        assertEquals(ErrorCode.KB_NOT_FOUND, e.getErrorCode());
    }

    @Test
    void parseDetailOfOtherUserFileResultShouldReject40401() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        // 归属看的是知识库归属：换一个登录用户，越权与"不存在"同样返回 40401
        SecurityTestSupport.loginOtherUser();

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.parseDetail(10L, null));
        assertEquals(ErrorCode.KB_NOT_FOUND, e.getErrorCode());
    }

    /** 让指定知识库归当前登录用户所有（归属校验要能过） */
    private void boundKnowledgeBase(Long id) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(id);
        kb.setUserId(SecurityTestSupport.VIEWER_ID);
        lenient().when(knowledgeBaseDbService.getActiveById(id)).thenReturn(kb);
    }

}
