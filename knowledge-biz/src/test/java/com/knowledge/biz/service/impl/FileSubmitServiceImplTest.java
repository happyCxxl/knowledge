package com.knowledge.biz.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.biz.service.db.KbSourceFileDbService;
import com.knowledge.biz.service.db.KbSubmitLogDbService;
import com.knowledge.biz.service.db.KnowledgeBaseDbService;
import com.knowledge.biz.service.support.InputVoAssembler;
import com.knowledge.biz.service.support.TaskVoAssembler;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.entity.KbSourceFile;
import com.knowledge.common.domain.entity.KbSubmitLog;
import com.knowledge.common.domain.entity.KnowledgeBase;
import com.knowledge.common.domain.input.FileMetadata;
import com.knowledge.common.domain.input.FileValidationResult;
import com.knowledge.common.dto.request.input.FileSubmitRequest;
import com.knowledge.common.dto.response.input.FileResultVO;
import com.knowledge.common.dto.response.input.FileSubmitResponse;
import com.knowledge.common.dto.response.task.StageStatusVO;
import com.knowledge.common.enums.input.FileFormat;
import com.knowledge.common.enums.input.FileValidationFailReason;
import com.knowledge.common.enums.input.SubmitStatus;
import com.knowledge.common.enums.knowledge.KnowledgeBaseStatus;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import com.knowledge.filecenter.service.FileStorage;
import com.knowledge.worker.input.FileValidatorPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 提交链路单测。
 *
 * @author cxxl
 */
@ExtendWith(MockitoExtension.class)
class FileSubmitServiceImplTest {

    @Mock
    private KnowledgeBaseDbService knowledgeBaseDbService;
    @Mock
    private KbSourceFileDbService sourceFileDbService;
    @Mock
    private KbFileResultDbService fileResultDbService;
    @Mock
    private KbSubmitLogDbService submitLogDbService;
    @Mock
    private KbPipelineTaskDbService pipelineTaskDbService;
    @Mock
    private FileValidatorPort fileValidator;
    @Mock
    private FileStorage fileStorage;

    private FileSubmitServiceImpl service;

    @BeforeEach
    void setUp() {
        // 组装器为纯映射无状态类，用真实实例（mock 会让 VO 组装返回 null，无法验证响应内容）
        service = new FileSubmitServiceImpl(knowledgeBaseDbService, sourceFileDbService, fileResultDbService,
                submitLogDbService, pipelineTaskDbService, fileValidator, fileStorage,
                new InputVoAssembler(), new TaskVoAssembler());
    }

    private FileSubmitRequest request() {
        FileSubmitRequest request = new FileSubmitRequest();
        request.setFileId("88");
        request.setRequestId("req-1");
        return request;
    }

    private KnowledgeBase activeKb() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(1L);
        kb.setStatus(KnowledgeBaseStatus.ACTIVE.getCode());
        return kb;
    }

    private FileValidationResult passResult() {
        return FileValidationResult.pass(FileFormat.PDF, "application/pdf", 100L, "sha256-abc");
    }

    private FileMetadata metadata() {
        FileMetadata metadata = new FileMetadata();
        metadata.setFileName("招标文件.pdf");
        metadata.setFileSize(100L);
        return metadata;
    }

    private void stubSuccessWrites() {
        when(sourceFileDbService.findByFileId("88")).thenReturn(null);
        when(sourceFileDbService.save(any(KbSourceFile.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbSourceFile.class).setId(1L);
            return true;
        });
        when(fileResultDbService.save(any(KbFileResult.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbFileResult.class).setId(10L);
            return true;
        });
        when(submitLogDbService.save(any(KbSubmitLog.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbSubmitLog.class).setId(30L);
            return true;
        });
    }

    @Test
    void submitSuccessShouldWriteThreeWithoutTask() {
        when(submitLogDbService.getByRequestId("req-1")).thenReturn(null);
        when(knowledgeBaseDbService.getActiveById(1L)).thenReturn(activeKb());
        when(fileValidator.validate("88")).thenReturn(passResult());
        when(fileStorage.metadata("88")).thenReturn(metadata());
        stubSuccessWrites();

        FileSubmitResponse response = service.submit(1L, request());

        assertNotNull(response.getSubmitLog());
        assertEquals(SubmitStatus.PASS.name(), response.getSubmitLog().getStatus());
        // 手动逐环节口径：提交只建档三写，不登记任务（解析由页面触发）
        assertNull(response.getPipelineTaskId());
        verify(fileResultDbService).save(any(KbFileResult.class));
        verify(pipelineTaskDbService, never()).save(any(KbPipelineTask.class));
    }

    @Test
    void submitValidationFailShouldOnlyWriteFailLog() {
        when(submitLogDbService.getByRequestId("req-1")).thenReturn(null);
        when(knowledgeBaseDbService.getActiveById(1L)).thenReturn(activeKb());
        when(fileValidator.validate("88"))
                .thenReturn(FileValidationResult.fail(FileValidationFailReason.FORMAT_NOT_ALLOWED));
        when(submitLogDbService.save(any(KbSubmitLog.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbSubmitLog.class).setId(30L);
            return true;
        });

        FileSubmitResponse response = service.submit(1L, request());

        assertEquals(SubmitStatus.FAIL.name(), response.getSubmitLog().getStatus());
        assertEquals(FileValidationFailReason.FORMAT_NOT_ALLOWED.name(), response.getSubmitLog().getFailReason());
        assertNull(response.getPipelineTaskId());
        verify(fileResultDbService, never()).save(any(KbFileResult.class));
        verify(pipelineTaskDbService, never()).save(any(KbPipelineTask.class));
    }

    @Test
    void submitFileNotFoundShouldRecordFailLog() {
        when(submitLogDbService.getByRequestId("req-1")).thenReturn(null);
        when(knowledgeBaseDbService.getActiveById(1L)).thenReturn(activeKb());
        when(fileValidator.validate("88"))
                .thenReturn(FileValidationResult.fail(FileValidationFailReason.FILE_NOT_FOUND));
        when(submitLogDbService.save(any(KbSubmitLog.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbSubmitLog.class).setId(30L);
            return true;
        });

        FileSubmitResponse response = service.submit(1L, request());

        assertEquals(SubmitStatus.FAIL.name(), response.getSubmitLog().getStatus());
        assertEquals(FileValidationFailReason.FILE_NOT_FOUND.name(), response.getSubmitLog().getFailReason());
        verify(fileResultDbService, never()).save(any(KbFileResult.class));
    }

    @Test
    void submitIdempotentReplayShouldReturnExisting() {
        KbSubmitLog existing = new KbSubmitLog();
        existing.setId(30L);
        existing.setRequestId("req-1");
        existing.setFileResultId(10L);
        existing.setStatus(SubmitStatus.PASS.name());
        when(submitLogDbService.getByRequestId("req-1")).thenReturn(existing);
        KbPipelineTask task = new KbPipelineTask();
        task.setId(20L);
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.PARSE.name())).thenReturn(task);

        FileSubmitResponse response = service.submit(1L, request());

        assertEquals(30L, response.getSubmitLog().getId());
        assertEquals(20L, response.getPipelineTaskId());
        verify(fileValidator, never()).validate(any());
        verify(knowledgeBaseDbService, never()).getActiveById(any());
    }

    @Test
    void submitLogDuplicateKeyShouldReplayExisting() {
        when(knowledgeBaseDbService.getActiveById(1L)).thenReturn(activeKb());
        when(fileValidator.validate("88")).thenReturn(passResult());
        when(fileStorage.metadata("88")).thenReturn(metadata());
        when(sourceFileDbService.findByFileId("88")).thenReturn(null);
        when(sourceFileDbService.save(any(KbSourceFile.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbSourceFile.class).setId(1L);
            return true;
        });
        when(fileResultDbService.save(any(KbFileResult.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbFileResult.class).setId(10L);
            return true;
        });
        // 并发冲突：save 撞唯一键，重查拿到对方已提交记录
        when(submitLogDbService.save(any(KbSubmitLog.class))).thenThrow(new DuplicateKeyException("uk_request_id"));
        KbSubmitLog raced = new KbSubmitLog();
        raced.setId(30L);
        raced.setRequestId("req-1");
        raced.setFileResultId(10L);
        raced.setStatus(SubmitStatus.PASS.name());
        when(submitLogDbService.getByRequestId("req-1")).thenReturn(null, raced);
        KbPipelineTask task = new KbPipelineTask();
        task.setId(20L);
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.PARSE.name())).thenReturn(task);

        FileSubmitResponse response = service.submit(1L, request());

        assertEquals(30L, response.getSubmitLog().getId());
        assertEquals(20L, response.getPipelineTaskId());
    }

    @Test
    void sourceFileDuplicateKeyShouldReuseExistingRow() {
        when(submitLogDbService.getByRequestId("req-1")).thenReturn(null);
        when(knowledgeBaseDbService.getActiveById(1L)).thenReturn(activeKb());
        when(fileValidator.validate("88")).thenReturn(passResult());
        when(fileStorage.metadata("88")).thenReturn(metadata());
        // 同 fileId 并发建档：先查无、保存撞 uk_file_id、重查复用
        when(sourceFileDbService.save(any(KbSourceFile.class))).thenThrow(new DuplicateKeyException("uk_file_id"));
        KbSourceFile existing = new KbSourceFile();
        existing.setId(7L);
        existing.setFileId("88");
        when(sourceFileDbService.findByFileId("88")).thenReturn(null, existing);
        when(fileResultDbService.save(any(KbFileResult.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbFileResult.class).setId(10L);
            return true;
        });
        when(submitLogDbService.save(any(KbSubmitLog.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbSubmitLog.class).setId(30L);
            return true;
        });

        FileSubmitResponse response = service.submit(1L, request());

        assertNull(response.getPipelineTaskId());
        ArgumentCaptor<KbFileResult> captor = ArgumentCaptor.forClass(KbFileResult.class);
        verify(fileResultDbService).save(captor.capture());
        assertEquals(7L, captor.getValue().getSourceFileId());
    }

    @Test
    void submitToDisabledKbShouldThrow() {
        when(submitLogDbService.getByRequestId("req-1")).thenReturn(null);
        KnowledgeBase disabled = new KnowledgeBase();
        disabled.setId(1L);
        disabled.setStatus(KnowledgeBaseStatus.DISABLED.getCode());
        when(knowledgeBaseDbService.getActiveById(1L)).thenReturn(disabled);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.submit(1L, request()));
        assertEquals(ErrorCode.KB_NOT_ACTIVE, e.getErrorCode());
    }

    @Test
    void submitMissingRequestIdShouldThrow() {
        FileSubmitRequest request = request();
        request.setRequestId("  ");

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.submit(1L, request));
        assertEquals(ErrorCode.REQUEST_ID_MISSING, e.getErrorCode());
    }

    @Test
    void pageFileResultsShouldAttachLatestStageStatuses() {
        KbFileResult fr10 = new KbFileResult();
        fr10.setId(10L);
        fr10.setSourceFileId(1L);
        KbFileResult fr11 = new KbFileResult();
        fr11.setId(11L);
        fr11.setSourceFileId(1L);
        Page<KbFileResult> page = new Page<>(1, 10);
        page.setRecords(List.of(fr10, fr11));
        when(fileResultDbService.pageByKb(1L, 10L, 1L)).thenReturn(page);
        KbSourceFile source = new KbSourceFile();
        source.setId(1L);
        source.setFileId("88");
        source.setFileName("招标文件.pdf");
        when(sourceFileDbService.listByIds(List.of(1L))).thenReturn(List.of(source));

        // 模拟 DB 倒序契约：同一 fileResult 两条任务时列表第一条即最新
        KbPipelineTask parseOld = new KbPipelineTask();
        parseOld.setId(2L);
        parseOld.setFileResultId(10L);
        parseOld.setStage(PipelineStage.PARSE.name());
        parseOld.setStatus(PipelineTaskStatus.FAILED.name());
        KbPipelineTask parseNew = new KbPipelineTask();
        parseNew.setId(21L);
        parseNew.setFileResultId(10L);
        parseNew.setStage(PipelineStage.PARSE.name());
        parseNew.setStatus(PipelineTaskStatus.SUCCESS.name());
        KbPipelineTask fr11Parse = new KbPipelineTask();
        fr11Parse.setId(22L);
        fr11Parse.setFileResultId(11L);
        fr11Parse.setStage(PipelineStage.PARSE.name());
        fr11Parse.setStatus(PipelineTaskStatus.RUNNING.name());
        when(pipelineTaskDbService.listByFileResultIdsAndStage(List.of(10L, 11L), PipelineStage.PARSE.name()))
                .thenReturn(List.of(parseNew, parseOld, fr11Parse));
        KbPipelineTask structure = new KbPipelineTask();
        structure.setId(51L);
        structure.setFileResultId(10L);
        structure.setStage(PipelineStage.STRUCTURE.name());
        structure.setStatus(PipelineTaskStatus.SUCCESS.name());
        when(pipelineTaskDbService.listByFileResultIdsAndStage(List.of(10L, 11L), PipelineStage.STRUCTURE.name()))
                .thenReturn(List.of(structure));
        KbPipelineTask preprocess = new KbPipelineTask();
        preprocess.setId(61L);
        preprocess.setFileResultId(10L);
        preprocess.setStage(PipelineStage.PREPROCESS.name());
        preprocess.setStatus(PipelineTaskStatus.SUCCESS.name());
        when(pipelineTaskDbService.listByFileResultIdsAndStage(List.of(10L, 11L), PipelineStage.PREPROCESS.name()))
                .thenReturn(List.of(preprocess));
        KbPipelineTask chunk = new KbPipelineTask();
        chunk.setId(71L);
        chunk.setFileResultId(10L);
        chunk.setStage(PipelineStage.CHUNK.name());
        chunk.setStatus(PipelineTaskStatus.SUCCESS.name());
        when(pipelineTaskDbService.listByFileResultIdsAndStage(List.of(10L, 11L), PipelineStage.CHUNK.name()))
                .thenReturn(List.of(chunk));
        KbPipelineTask embed = new KbPipelineTask();
        embed.setId(81L);
        embed.setFileResultId(10L);
        embed.setStage(PipelineStage.EMBED.name());
        embed.setStatus(PipelineTaskStatus.SUCCESS.name());
        when(pipelineTaskDbService.listByFileResultIdsAndStage(List.of(10L, 11L), PipelineStage.EMBED.name()))
                .thenReturn(List.of(embed));

        IPage<FileResultVO> result = service.pageFileResults(1L, 10L, 1L, null);

        List<FileResultVO> records = result.getRecords();
        assertEquals(2, records.size());
        // fr10：PARSE 取最新一条 + STRUCTURE + PREPROCESS + CHUNK + EMBED，按管线顺序排列（五环节齐）
        List<StageStatusVO> fr10Statuses = records.getFirst().getStageStatuses();
        assertNotNull(fr10Statuses);
        assertEquals(5, fr10Statuses.size());
        assertEquals(PipelineStage.PARSE.name(), fr10Statuses.getFirst().getStage());
        assertEquals(21L, fr10Statuses.get(0).getTaskId());
        assertEquals(PipelineTaskStatus.SUCCESS.name(), fr10Statuses.get(0).getStatus());
        assertEquals(PipelineStage.STRUCTURE.name(), fr10Statuses.get(1).getStage());
        assertEquals(51L, fr10Statuses.get(1).getTaskId());
        assertEquals(PipelineStage.PREPROCESS.name(), fr10Statuses.get(2).getStage());
        assertEquals(61L, fr10Statuses.get(2).getTaskId());
        assertEquals(PipelineStage.CHUNK.name(), fr10Statuses.get(3).getStage());
        assertEquals(71L, fr10Statuses.get(3).getTaskId());
        assertEquals(PipelineStage.EMBED.name(), fr10Statuses.get(4).getStage());
        assertEquals(81L, fr10Statuses.get(4).getTaskId());
        // fr11：仅 PARSE 一条
        List<StageStatusVO> fr11Statuses = records.get(1).getStageStatuses();
        assertNotNull(fr11Statuses);
        assertEquals(1, fr11Statuses.size());
        assertEquals(22L, fr11Statuses.getFirst().getTaskId());
    }

    @Test
    void pageFileResultsShouldValidateStageOnly() {
        when(fileResultDbService.pageByKb(1L, 10L, 1L))
                .thenReturn(new Page<>(1, 10));

        // 合法 stage（PARSE/STRUCTURE/空）只做合法性校验；按上游产物过滤在解析环节落地后启用
        service.pageFileResults(1L, 10L, 1L, "PARSE");
        service.pageFileResults(1L, 10L, 1L, "STRUCTURE");
        service.pageFileResults(1L, 10L, 1L, null);
        service.pageFileResults(1L, 10L, 1L, "");

        verify(fileResultDbService, times(4)).pageByKb(1L, 10L, 1L);
    }

    @Test
    void pageFileResultsShouldRejectUnknownStage() {
        KnowledgeException e = assertThrows(KnowledgeException.class,
                () -> service.pageFileResults(1L, 10L, 1L, "INDEX"));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
        verify(fileResultDbService, never()).pageByKb(anyLong(), anyLong(), any());
    }
}
