package com.knowledge.biz.service.impl;

import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineStepLogDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.biz.service.support.StructureVoAssembler;
import com.knowledge.biz.service.support.TaskDetailSupport;
import com.knowledge.biz.task.TaskQueueSupport;
import com.knowledge.biz.task.TaskTriggerSupport;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineStepLog;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.dto.response.structure.StructureDetailVO;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import com.knowledge.filecenter.service.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
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
 * 结构组装控制面服务单测：上游产物校验 / 防重 / 建任务入队（upstream 链）/ 组装详情。
 *
 * @author cxxl
 */
@ExtendWith(MockitoExtension.class)
class StructureControlServiceImplTest {

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
    private FileStorage fileStorage;

    private StructureControlServiceImpl service;

    @BeforeEach
    void setUp() {
        // 触发/详情助手为纯委托类、组装器为纯映射类，用真实实例（mock 会让 VO 组装返回 null，断言失真）
        service = new StructureControlServiceImpl(fileResultDbService, pipelineProductDbService,
                stepLogDbService, new TaskTriggerSupport(pipelineTaskDbService, taskQueue),
                new TaskDetailSupport(pipelineTaskDbService), fileStorage,
                new StructureVoAssembler());
    }

    private KbFileResult fileResult() {
        KbFileResult fileResult = new KbFileResult();
        fileResult.setId(10L);
        return fileResult;
    }

    @Test
    void structureShouldCreateTaskWithUpstream() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        KbPipelineProduct parseProduct = new KbPipelineProduct();
        parseProduct.setId(50L);
        parseProduct.setArtifactId("abc");
        // 缺省路径：取该文件结果最新 PARSE 产物
        when(pipelineProductDbService.getByFileResultIdAndStage(10L, PipelineStage.PARSE.name()))
                .thenReturn(parseProduct);
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.STRUCTURE.name()))
                .thenReturn(null);
        when(pipelineTaskDbService.save(any(KbPipelineTask.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbPipelineTask.class).setId(51L);
            return true;
        });

        var response = service.structure(10L, null);

        assertEquals(51L, response.getPipelineTaskId());
        ArgumentCaptor<KbPipelineTask> captor = ArgumentCaptor.forClass(KbPipelineTask.class);
        verify(pipelineTaskDbService).save(captor.capture());
        assertEquals(PipelineStage.STRUCTURE.name(), captor.getValue().getStage());
        assertEquals(50L, captor.getValue().getUpstreamProductId());
        verify(taskQueue).enqueue(51L);
    }

    @Test
    void structureShouldUseSpecifiedUpstreamProduct() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        KbPipelineProduct specified = new KbPipelineProduct();
        specified.setId(88L);
        specified.setFileResultId(10L);
        specified.setStage(PipelineStage.PARSE.name());
        // 指定路径：按 id 查产物并校验
        when(pipelineProductDbService.getById(88L)).thenReturn(specified);
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.STRUCTURE.name()))
                .thenReturn(null);
        when(pipelineTaskDbService.save(any(KbPipelineTask.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbPipelineTask.class).setId(89L);
            return true;
        });

        var response = service.structure(10L, 88L);

        assertEquals(89L, response.getPipelineTaskId());
        ArgumentCaptor<KbPipelineTask> captor = ArgumentCaptor.forClass(KbPipelineTask.class);
        verify(pipelineTaskDbService).save(captor.capture());
        assertEquals(88L, captor.getValue().getUpstreamProductId());
    }

    @Test
    void structureWithMismatchedProductShouldReject() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        KbPipelineProduct wrongStage = new KbPipelineProduct();
        wrongStage.setId(88L);
        wrongStage.setFileResultId(10L);
        wrongStage.setStage(PipelineStage.CHUNK.name());
        when(pipelineProductDbService.getById(88L)).thenReturn(wrongStage);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.structure(10L, 88L));
        assertEquals(ErrorCode.FILE_RESULT_NOT_FOUND, e.getErrorCode());
        verify(pipelineTaskDbService, never()).save(any());
    }

    @Test
    void structureWithoutParseProductShouldReject() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        when(pipelineProductDbService.getByFileResultIdAndStage(10L, PipelineStage.PARSE.name()))
                .thenReturn(null);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.structure(10L, null));
        assertEquals(ErrorCode.FILE_RESULT_NOT_FOUND, e.getErrorCode());
        verify(pipelineTaskDbService, never()).save(any());
    }

    @Test
    void structureRunningShouldReject() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        KbPipelineProduct parseProduct = new KbPipelineProduct();
        parseProduct.setId(50L);
        when(pipelineProductDbService.getByFileResultIdAndStage(10L, PipelineStage.PARSE.name()))
                .thenReturn(parseProduct);
        KbPipelineTask running = new KbPipelineTask();
        running.setId(51L);
        running.setStatus(PipelineTaskStatus.RUNNING.name());
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.STRUCTURE.name()))
                .thenReturn(running);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.structure(10L, null));
        assertEquals(ErrorCode.TASK_ALREADY_PENDING, e.getErrorCode());
        verify(pipelineTaskDbService, never()).save(any());
        verify(taskQueue, never()).enqueue(any());
    }

    @Test
    void structureQueuedShouldEnqueueExistingWithoutNewTask() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        KbPipelineProduct parseProduct = new KbPipelineProduct();
        parseProduct.setId(50L);
        when(pipelineProductDbService.getByFileResultIdAndStage(10L, PipelineStage.PARSE.name()))
                .thenReturn(parseProduct);
        KbPipelineTask queued = new KbPipelineTask();
        queued.setId(51L);
        queued.setStatus(PipelineTaskStatus.QUEUED.name());
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.STRUCTURE.name()))
                .thenReturn(queued);

        var response = service.structure(10L, null);

        assertEquals(51L, response.getPipelineTaskId());
        verify(pipelineTaskDbService, never()).save(any());
        verify(taskQueue).enqueue(51L);
    }

    @Test
    void detailShouldAssembleTaskStepsSummaryAndOutline() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        KbPipelineTask task = new KbPipelineTask();
        task.setId(51L);
        task.setStage(PipelineStage.STRUCTURE.name());
        task.setStatus(PipelineTaskStatus.PARTIAL_SUCCESS.name());
        task.setProductId(50L);
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.STRUCTURE.name())).thenReturn(task);
        KbPipelineStepLog step = new KbPipelineStepLog();
        step.setStepName("结构组装");
        step.setStatus("SUCCESS");
        step.setWarningCount(1);
        step.setMatchedCount(5);
        step.setChangedCount(2);
        step.setAvgLen(400);
        when(stepLogDbService.listByTaskId(51L)).thenReturn(List.of(step));
        KbPipelineProduct product = new KbPipelineProduct();
        product.setArtifactId("def".repeat(16));
        product.setContentHash("def".repeat(16));
        when(pipelineProductDbService.getById(50L)).thenReturn(product);
        String json = "{\"elements\":["
                + "{\"id\":\"h-1\",\"type\":\"TITLE\",\"text\":\"第一章\",\"level\":1},"
                + "{\"id\":\"n-1\",\"type\":\"PARAGRAPH\",\"text\":\"正文\",\"conflictStatus\":\"PRIMARY\"},"
                + "{\"id\":\"t-1\",\"type\":\"TABLE\",\"rows\":2,\"cols\":2,\"cells\":["
                + "{\"row\":0,\"col\":0,\"text\":\"A\",\"isHeader\":true},"
                + "{\"row\":0,\"col\":1,\"text\":\"B\",\"isHeader\":true}]}],"
                + "\"relations\":[{\"type\":\"PARENT_CHILD\",\"from\":\"h-1\",\"to\":\"n-1\"}],"
                + "\"quality\":{\"warnings\":[{\"code\":\"NOISE_PAGE\",\"level\":\"WARN\",\"message\":\"噪声页\"}],"
                + "\"conflicts\":[{\"primaryElementId\":\"n-1\",\"backupElementId\":\"n-2\",\"message\":\"无法裁决\"}]}}";
        when(fileStorage.getObject("def".repeat(16))).thenReturn(json.getBytes(StandardCharsets.UTF_8));

        StructureDetailVO detail = service.structureDetail(10L, null);

        assertEquals(51L, detail.getTaskId());
        assertEquals(PipelineTaskStatus.PARTIAL_SUCCESS.name(), detail.getStatus());
        assertEquals(1, detail.getSteps().size());
        assertEquals(1, detail.getSteps().getFirst().getWarningCount());
        assertEquals(5, detail.getSteps().getFirst().getMatchedCount());
        assertEquals(2, detail.getSteps().getFirst().getChangedCount());
        assertEquals(400, detail.getSteps().getFirst().getAvgLen());
        assertNotNull(detail.getSummary());
        assertEquals(3, detail.getSummary().getElementCount());
        assertEquals(1, detail.getSummary().getTitleCount());
        assertEquals(1, detail.getSummary().getRelationCount());
        assertEquals(1, detail.getSummary().getConflictCount());
        assertEquals(1, detail.getWarnings().size());
        assertEquals("n-1", detail.getConflicts().getFirst().getPrimaryElementId());
        assertEquals(3, detail.getOutline().size());
        assertEquals("TITLE", detail.getOutline().getFirst().getType());
        assertEquals("第一章", detail.getOutline().getFirst().getText());
        assertEquals("PRIMARY", detail.getOutline().get(1).getConflictStatus());
        assertEquals(2, detail.getOutline().get(2).getCells().size());
        assertTrue(detail.getOutline().get(2).getCells().getFirst().getIsHeader());
    }

    @Test
    void detailWithoutTaskShouldReturnEmptyCollections() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.STRUCTURE.name())).thenReturn(null);

        StructureDetailVO detail = service.structureDetail(10L, null);

        assertNotNull(detail.getWarnings());
        assertTrue(detail.getWarnings().isEmpty());
        assertNotNull(detail.getConflicts());
        assertNotNull(detail.getOutline());
    }

    @Test
    void detailWithWrongStageTaskShouldReject() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        KbPipelineTask parseTask = new KbPipelineTask();
        parseTask.setId(20L);
        parseTask.setFileResultId(10L);
        parseTask.setStage(PipelineStage.PARSE.name());
        when(pipelineTaskDbService.getById(20L)).thenReturn(parseTask);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.structureDetail(10L, 20L));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
    }
}
