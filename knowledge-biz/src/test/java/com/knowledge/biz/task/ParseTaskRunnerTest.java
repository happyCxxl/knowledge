package com.knowledge.biz.task;

import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineStepLogDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.biz.service.db.KbSourceFileDbService;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.entity.KbSourceFile;
import com.knowledge.common.domain.parse.ParseOutcome;
import com.knowledge.common.domain.parse.ParseResult;
import com.knowledge.common.domain.task.StepLogInfo;
import com.knowledge.common.enums.task.PipelineTaskErrorCode;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import com.knowledge.filecenter.service.FileStorage;
import com.knowledge.worker.parser.ParseContext;
import com.knowledge.worker.parser.ParseProperties;
import com.knowledge.worker.parser.impl.ParsePipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 解析任务执行器单测：领任务防重、成功/失败回写、产物与子步骤落库。
 *
 * @author cxxl
 */
@ExtendWith(MockitoExtension.class)
class ParseTaskRunnerTest {

    @Mock
    private KbPipelineTaskDbService pipelineTaskDbService;
    @Mock
    private KbFileResultDbService fileResultDbService;
    @Mock
    private KbSourceFileDbService sourceFileDbService;
    @Mock
    private KbPipelineProductDbService pipelineProductDbService;
    @Mock
    private KbPipelineStepLogDbService stepLogDbService;
    @Mock
    private FileStorage fileStorage;
    @Mock
    private ParsePipeline parsePipeline;

    private ParseTaskRunner runner;

    @BeforeEach
    void setUp() {
        runner = new ParseTaskRunner(pipelineTaskDbService, fileResultDbService, sourceFileDbService,
                pipelineProductDbService, fileStorage, parsePipeline,
                new ParseProperties(), new StepLogPersistence(stepLogDbService));
    }

    private KbPipelineTask queuedTask() {
        KbPipelineTask task = new KbPipelineTask();
        task.setId(20L);
        task.setFileResultId(10L);
        task.setStatus(PipelineTaskStatus.QUEUED.name());
        return task;
    }

    private void stubFileChain() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        when(sourceFileDbService.getById(5L)).thenReturn(sourceFile());
        when(fileStorage.open("F-88")).thenReturn(new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)));
    }

    private KbFileResult fileResult() {
        KbFileResult fileResult = new KbFileResult();
        fileResult.setId(10L);
        fileResult.setSourceFileId(5L);
        return fileResult;
    }

    private KbSourceFile sourceFile() {
        KbSourceFile sourceFile = new KbSourceFile();
        sourceFile.setFileId("F-88");
        sourceFile.setFileName("招标文件.pdf");
        sourceFile.setSha256("abc");
        sourceFile.setMimeType("application/pdf");
        return sourceFile;
    }

    private ParseOutcome successOutcome() {
        ParseOutcome outcome = new ParseOutcome();
        outcome.setSuggestedStatus(PipelineTaskStatus.SUCCESS.name());
        outcome.setUnitCount(3);
        ParseResult parseResult = new ParseResult();
        parseResult.setResultId(10L);
        outcome.setParseResult(parseResult);
        StepLogInfo step = new StepLogInfo();
        step.setStepName("原生解析");
        step.setStatus("SUCCESS");
        step.setStartedAt(LocalDateTime.now());
        step.setFinishedAt(LocalDateTime.now());
        step.setDuration(10);
        outcome.setStepLogs(List.of(step));
        return outcome;
    }

    @Test
    void nonQueuedTaskShouldSkip() {
        KbPipelineTask task = queuedTask();
        task.setStatus(PipelineTaskStatus.SUCCESS.name());
        when(pipelineTaskDbService.getById(20L)).thenReturn(task);

        runner.run(20L);

        verify(pipelineTaskDbService, never()).claim(any());
    }

    @Test
    void claimLostShouldSkip() {
        when(pipelineTaskDbService.getById(20L)).thenReturn(queuedTask());
        when(pipelineTaskDbService.claim(20L)).thenReturn(0);

        runner.run(20L);

        verify(fileResultDbService, never()).getById(any());
    }

    @Test
    void successPathShouldPersistProductAndFinish() {
        when(pipelineTaskDbService.getById(20L)).thenReturn(queuedTask());
        when(pipelineTaskDbService.claim(20L)).thenReturn(1);
        stubFileChain();
        when(parsePipeline.run(any(ParseContext.class))).thenReturn(successOutcome());
        when(fileStorage.putObject(any(byte[].class))).thenReturn("9f2c".repeat(16));
        when(pipelineProductDbService.save(any(KbPipelineProduct.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbPipelineProduct.class).setId(40L);
            return true;
        });

        runner.run(20L);

        ArgumentCaptor<KbPipelineProduct> captor = ArgumentCaptor.forClass(KbPipelineProduct.class);
        verify(pipelineProductDbService).save(captor.capture());
        assertEquals(10L, captor.getValue().getFileResultId());
        assertEquals("PARSE", captor.getValue().getStage());
        assertEquals("9f2c".repeat(16), captor.getValue().getArtifactId());
        assertEquals("9f2c".repeat(16), captor.getValue().getContentHash());
        verify(pipelineTaskDbService).finish(20L, PipelineTaskStatus.SUCCESS.name(), null, null);
        verify(pipelineTaskDbService).updateProductId(20L, 40L);
        // 手动逐环节口径：PARSE 完成后停在终态，不再自动登记 STRUCTURE
        verify(pipelineTaskDbService, never()).save(any(KbPipelineTask.class));
        verify(stepLogDbService).save(any());
    }

    @Test
    void scannedOutcomeShouldFinishFailedWithoutProduct() {
        when(pipelineTaskDbService.getById(20L)).thenReturn(queuedTask());
        when(pipelineTaskDbService.claim(20L)).thenReturn(1);
        stubFileChain();
        ParseOutcome outcome = new ParseOutcome();
        outcome.setSuggestedStatus(PipelineTaskStatus.FAILED.name());
        outcome.setErrorCode(PipelineTaskErrorCode.SCANNED_UNSUPPORTED.name());
        outcome.setErrorMsg("扫描件暂不支持（OCR 预留）");
        when(parsePipeline.run(any(ParseContext.class))).thenReturn(outcome);

        runner.run(20L);

        verify(pipelineTaskDbService).finish(20L, PipelineTaskStatus.FAILED.name(),
                PipelineTaskErrorCode.SCANNED_UNSUPPORTED.name(), "扫描件暂不支持（OCR 预留）");
        verify(pipelineProductDbService, never()).save(any());
    }

    @Test
    void missingFileResultShouldFail() {
        when(pipelineTaskDbService.getById(20L)).thenReturn(queuedTask());
        when(pipelineTaskDbService.claim(20L)).thenReturn(1);
        when(fileResultDbService.getById(10L)).thenReturn(null);

        runner.run(20L);

        verify(pipelineTaskDbService).finish(eq(20L), eq(PipelineTaskStatus.FAILED.name()),
                eq(PipelineTaskErrorCode.PARSE_FAILED.name()), any());
    }

    @Test
    void fileGoneShouldFailWithClearReason() {
        when(pipelineTaskDbService.getById(20L)).thenReturn(queuedTask());
        when(pipelineTaskDbService.claim(20L)).thenReturn(1);
        stubFileChain();
        when(parsePipeline.run(any(ParseContext.class)))
                .thenThrow(new KnowledgeException(ErrorCode.FILE_NOT_FOUND));

        runner.run(20L);

        verify(pipelineTaskDbService).finish(eq(20L), eq(PipelineTaskStatus.FAILED.name()),
                eq(PipelineTaskErrorCode.PARSE_FAILED.name()), any());
    }
}
