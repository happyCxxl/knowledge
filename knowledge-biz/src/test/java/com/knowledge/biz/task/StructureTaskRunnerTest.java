package com.knowledge.biz.task;

import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineStepLogDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.biz.service.db.KbSourceFileDbService;
import com.knowledge.biz.service.support.ChainStorageSupport;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.entity.KbSourceFile;
import com.knowledge.common.domain.parse.ParseResult;
import com.knowledge.common.domain.storage.ObjectRef;
import com.knowledge.common.domain.storage.StorageRef;
import com.knowledge.common.domain.structure.AssembleOutcome;
import com.knowledge.common.domain.structure.UnifiedDocument;
import com.knowledge.common.enums.storage.StorageType;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.PipelineTaskErrorCode;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.filecenter.provider.StorageRouter;
import com.knowledge.filecenter.service.FileStorage;
import com.knowledge.worker.structure.AssembleContext;
import com.knowledge.worker.structure.DocumentAssemblerPort;
import com.knowledge.worker.structure.StructureProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 统一组装任务执行器单测：成功落库（upstream 链）/ 空树失败 / 上游缺失。
 *
 * @author cxxl
 */
@ExtendWith(MockitoExtension.class)
class StructureTaskRunnerTest {

    /** 档案与产物所在的数据源 */
    private static final Long SOURCE_ID = 1L;

    @Mock
    private KbPipelineTaskDbService pipelineTaskDbService;
    @Mock
    private KbPipelineProductDbService pipelineProductDbService;
    @Mock
    private KbPipelineStepLogDbService stepLogDbService;
    @Mock
    private KbFileResultDbService fileResultDbService;
    @Mock
    private KbSourceFileDbService sourceFileDbService;
    @Mock
    private FileStorage fileStorage;
    @Mock
    private StorageRouter storageRouter;
    @Mock
    private DocumentAssemblerPort documentAssembler;

    private StructureTaskRunner runner;

    @BeforeEach
    void setUp() {
        // 存储守卫用真实实例 + mock 依赖：链类型取不到（默认 mock 返回 null）时不拦
        ChainStorageSupport chainStorage = new ChainStorageSupport(fileResultDbService, sourceFileDbService,
                fileStorage, storageRouter);
        runner = new StructureTaskRunner(pipelineTaskDbService, pipelineProductDbService,
                fileStorage, documentAssembler, new StructureProperties(),
                new StepLogPersistence(stepLogDbService),
                new ProductPersistence(pipelineProductDbService, pipelineTaskDbService, chainStorage, fileStorage));
    }

    private KbPipelineTask queuedTask() {
        KbPipelineTask task = new KbPipelineTask();
        task.setId(60L);
        task.setFileResultId(10L);
        task.setUpstreamProductId(50L);
        task.setStage(PipelineStage.STRUCTURE.name());
        task.setStatus(PipelineTaskStatus.QUEUED.name());
        return task;
    }

    private KbPipelineProduct parseProduct() {
        KbPipelineProduct product = new KbPipelineProduct();
        product.setId(50L);
        product.setArtifactId("abc".repeat(22));
        product.setStorageType(StorageType.MINIO.getCode());
        product.setStorageSourceId(SOURCE_ID);
        product.setBucket("artifacts");
        return product;
    }

    private AssembleOutcome successOutcome() {
        AssembleOutcome outcome = new AssembleOutcome();
        outcome.setSuggestedStatus(PipelineTaskStatus.SUCCESS.name());
        outcome.setDocument(new UnifiedDocument());
        return outcome;
    }

    /** 链的一环：文件结果 10 → 来源文件 5 */
    private KbFileResult fileResult() {
        KbFileResult fileResult = new KbFileResult();
        fileResult.setId(10L);
        fileResult.setSourceFileId(5L);
        return fileResult;
    }

    /** 链的一环：来源文件 5 → 文件档案 F-88 */
    private KbSourceFile sourceFile() {
        KbSourceFile sourceFile = new KbSourceFile();
        sourceFile.setId(5L);
        sourceFile.setFileId("F-88");
        return sourceFile;
    }

    @Test
    void successPathShouldPersistStructureProductWithUpstreamChain() {
        when(pipelineTaskDbService.getById(60L)).thenReturn(queuedTask());
        when(pipelineTaskDbService.claim(60L)).thenReturn(1);
        when(pipelineProductDbService.getById(50L)).thenReturn(parseProduct());
        when(fileStorage.getObject(ObjectRef.of(SOURCE_ID, StorageType.MINIO.getCode(), "artifacts", "abc".repeat(22))))
                .thenReturn(JsonUtil.toJsonStr(new ParseResult()).getBytes(StandardCharsets.UTF_8));
        when(documentAssembler.assemble(any(ParseResult.class), any(AssembleContext.class)))
                .thenReturn(successOutcome());
        when(fileStorage.putObject(any(byte[].class)))
                .thenReturn(ObjectRef.of(SOURCE_ID, StorageType.MINIO.getCode(), "artifacts", "def".repeat(21) + "0"));
        when(pipelineProductDbService.save(any(KbPipelineProduct.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbPipelineProduct.class).setId(70L);
            return true;
        });

        runner.run(60L);

        ArgumentCaptor<KbPipelineProduct> captor = ArgumentCaptor.forClass(KbPipelineProduct.class);
        verify(pipelineProductDbService).save(captor.capture());
        assertEquals(PipelineStage.STRUCTURE.name(), captor.getValue().getStage());
        assertEquals(50L, captor.getValue().getUpstreamProductId());
        assertEquals(10L, captor.getValue().getFileResultId());
        assertEquals("def".repeat(21) + "0", captor.getValue().getArtifactId());
        assertEquals("def".repeat(21) + "0", captor.getValue().getContentHash());
        // 产物行记下对象位置：读取路径据此定位（缺这几段会报 40455）
        assertEquals(StorageType.MINIO.getCode(), captor.getValue().getStorageType());
        assertEquals(SOURCE_ID, captor.getValue().getStorageSourceId());
        assertEquals("artifacts", captor.getValue().getBucket());
        verify(pipelineTaskDbService).finish(60L, PipelineTaskStatus.SUCCESS.name(), null, null);
        verify(pipelineTaskDbService).updateProductId(60L, 70L);
    }

    @Test
    void mismatchedStorageShouldFailTaskWithoutWritingProduct() {
        when(pipelineTaskDbService.getById(60L)).thenReturn(queuedTask());
        when(pipelineTaskDbService.claim(60L)).thenReturn(1);
        when(pipelineProductDbService.getById(50L)).thenReturn(parseProduct());
        when(fileStorage.getObject(ObjectRef.of(SOURCE_ID, StorageType.MINIO.getCode(), "artifacts", "abc".repeat(22))))
                .thenReturn(JsonUtil.toJsonStr(new ParseResult()).getBytes(StandardCharsets.UTF_8));
        when(documentAssembler.assemble(any(ParseResult.class), any(AssembleContext.class)))
                .thenReturn(successOutcome());
        // 链落在数据源 2，当前启用的是数据源 1：写入口拒绝，产物既不入存储也不落库
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        when(sourceFileDbService.getById(5L)).thenReturn(sourceFile());
        when(fileStorage.refOf("F-88")).thenReturn(new StorageRef(StorageType.LOCAL.getCode(), 2L));
        when(storageRouter.currentSourceId()).thenReturn(SOURCE_ID);

        runner.run(60L);

        verify(pipelineTaskDbService).finish(eq(60L), eq(PipelineTaskStatus.FAILED.name()),
                eq(PipelineTaskErrorCode.TASK_STORAGE_MISMATCH.name()), any());
        verify(fileStorage, never()).putObject(any(byte[].class));
        verify(pipelineProductDbService, never()).save(any());
    }

    @Test
    void emptyTreeShouldFailWithoutProduct() {
        when(pipelineTaskDbService.getById(60L)).thenReturn(queuedTask());
        when(pipelineTaskDbService.claim(60L)).thenReturn(1);
        when(pipelineProductDbService.getById(50L)).thenReturn(parseProduct());
        when(fileStorage.getObject(ObjectRef.of(SOURCE_ID, StorageType.MINIO.getCode(), "artifacts", "abc".repeat(22))))
                .thenReturn(JsonUtil.toJsonStr(new ParseResult()).getBytes(StandardCharsets.UTF_8));
        AssembleOutcome outcome = new AssembleOutcome();
        outcome.setSuggestedStatus(PipelineTaskStatus.FAILED.name());
        outcome.setErrorCode(PipelineTaskErrorCode.STRUCTURE_EMPTY.name());
        outcome.setErrorMsg("无任何可组装元素（空树）");
        when(documentAssembler.assemble(any(ParseResult.class), any(AssembleContext.class))).thenReturn(outcome);

        runner.run(60L);

        verify(pipelineTaskDbService).finish(eq(60L), eq(PipelineTaskStatus.FAILED.name()),
                eq(PipelineTaskErrorCode.STRUCTURE_EMPTY.name()), any());
        verify(pipelineProductDbService, never()).save(any());
        verify(fileStorage, never()).putObject(any(byte[].class));
    }

    @Test
    void missingUpstreamProductShouldFailUpstreamUnreadable() {
        when(pipelineTaskDbService.getById(60L)).thenReturn(queuedTask());
        when(pipelineTaskDbService.claim(60L)).thenReturn(1);
        when(pipelineProductDbService.getById(50L)).thenReturn(null);
        when(pipelineProductDbService.getByFileResultIdAndStage(10L, PipelineStage.PARSE.name())).thenReturn(null);

        runner.run(60L);

        // 上游拿不到记"上游不可读"，与"空树"分开，两者失败原因对用户可区分
        verify(pipelineTaskDbService).finish(eq(60L), eq(PipelineTaskStatus.FAILED.name()),
                eq(PipelineTaskErrorCode.STRUCTURE_UPSTREAM_UNREADABLE.name()), any());
        verify(fileStorage, never()).getObject(any());
    }

    @Test
    void unexpectedFailureShouldUseClassNameInsteadOfNull() {
        when(pipelineTaskDbService.getById(60L)).thenReturn(queuedTask());
        when(pipelineTaskDbService.claim(60L)).thenReturn(1);
        when(pipelineProductDbService.getById(50L)).thenReturn(parseProduct());
        when(fileStorage.getObject(ObjectRef.of(SOURCE_ID, StorageType.MINIO.getCode(), "artifacts", "abc".repeat(22))))
                .thenReturn(JsonUtil.toJsonStr(new ParseResult()).getBytes(StandardCharsets.UTF_8));
        when(documentAssembler.assemble(any(ParseResult.class), any(AssembleContext.class)))
                .thenThrow(new IllegalStateException());

        runner.run(60L);

        ArgumentCaptor<String> errorMsg = ArgumentCaptor.forClass(String.class);
        verify(pipelineTaskDbService).finish(eq(60L), eq(PipelineTaskStatus.FAILED.name()),
                eq(PipelineTaskErrorCode.STRUCTURE_FAILED.name()), errorMsg.capture());
        // 无消息的异常不落 "null"，退化为类名，不看日志也能判断异常类型
        assertEquals("IllegalStateException", errorMsg.getValue());
    }

    @Test
    void knowledgeExceptionOnAssembleShouldFailUpstreamUnreadable() {
        when(pipelineTaskDbService.getById(60L)).thenReturn(queuedTask());
        when(pipelineTaskDbService.claim(60L)).thenReturn(1);
        when(pipelineProductDbService.getById(50L)).thenReturn(parseProduct());
        when(fileStorage.getObject(ObjectRef.of(SOURCE_ID, StorageType.MINIO.getCode(), "artifacts", "abc".repeat(22))))
                .thenReturn(JsonUtil.toJsonStr(new ParseResult()).getBytes(StandardCharsets.UTF_8));
        when(documentAssembler.assemble(any(ParseResult.class), any(AssembleContext.class)))
                .thenThrow(new KnowledgeException(ErrorCode.FILE_NOT_FOUND));

        runner.run(60L);

        ArgumentCaptor<String> errorMsg = ArgumentCaptor.forClass(String.class);
        verify(pipelineTaskDbService).finish(eq(60L), eq(PipelineTaskStatus.FAILED.name()),
                eq(PipelineTaskErrorCode.STRUCTURE_UPSTREAM_UNREADABLE.name()), errorMsg.capture());
        // 业务异常（上游文件已不存在）按"上游不可读"归口，且直接用它的原因文案
        assertEquals(ErrorCode.FILE_NOT_FOUND.getMessage(), errorMsg.getValue());
    }

    @Test
    void unreadableUpstreamWithBlankMessageShouldStillNameTheCause() {
        when(pipelineTaskDbService.getById(60L)).thenReturn(queuedTask());
        when(pipelineTaskDbService.claim(60L)).thenReturn(1);
        when(pipelineProductDbService.getById(50L)).thenReturn(parseProduct());
        when(fileStorage.getObject(ObjectRef.of(SOURCE_ID, StorageType.MINIO.getCode(), "artifacts", "abc".repeat(22)))).thenThrow(new IllegalStateException());

        runner.run(60L);

        ArgumentCaptor<String> errorMsg = ArgumentCaptor.forClass(String.class);
        verify(pipelineTaskDbService).finish(eq(60L), eq(PipelineTaskStatus.FAILED.name()),
                eq(PipelineTaskErrorCode.STRUCTURE_UPSTREAM_UNREADABLE.name()), errorMsg.capture());
        assertEquals("上游解析产物读取失败: IllegalStateException", errorMsg.getValue());
    }
}
