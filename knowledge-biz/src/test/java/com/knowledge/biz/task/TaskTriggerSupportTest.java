package com.knowledge.biz.task;

import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.biz.service.db.KbSourceFileDbService;
import com.knowledge.biz.service.support.ChainStorageSupport;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.entity.KbSourceFile;
import com.knowledge.common.domain.storage.StorageRef;
import com.knowledge.common.dto.response.task.StageTriggerVO;
import com.knowledge.common.enums.storage.StorageType;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.PipelineTaskErrorCode;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 环节任务触发单测：存储一致性守卫（40453，委托 ChainStorageSupport）/ 新建任务落库入队 /
 * 入队失败落 FAILED 且异常继续抛出。
 *
 * @author cxxl
 */
@ExtendWith(MockitoExtension.class)
class TaskTriggerSupportTest {

    /** 链的档案所在数据源 */
    private static final Long ARCHIVE_SOURCE = 1L;

    /** 当前启用的数据源 */
    private static final Long CURRENT_SOURCE = 2L;

    @Mock
    private KbPipelineTaskDbService pipelineTaskDbService;
    @Mock
    private KbFileResultDbService fileResultDbService;
    @Mock
    private KbSourceFileDbService sourceFileDbService;
    @Mock
    private FileStorage fileStorage;
    @Mock
    private StorageRouter storageRouter;
    @Mock
    private TaskQueueSupport taskQueue;

    private TaskTriggerSupport triggerSupport;

    @BeforeEach
    void setUp() {
        triggerSupport = new TaskTriggerSupport(pipelineTaskDbService,
                new ChainStorageSupport(fileResultDbService, sourceFileDbService, fileStorage, storageRouter),
                taskQueue);
    }

    /** 链的源文件档案：fileResult 10 → sourceFile 5 → fileId F-88 */
    private void stubFileChain() {
        KbFileResult fileResult = new KbFileResult();
        fileResult.setId(10L);
        fileResult.setSourceFileId(5L);
        when(fileResultDbService.getById(10L)).thenReturn(fileResult);
        KbSourceFile sourceFile = new KbSourceFile();
        sourceFile.setId(5L);
        sourceFile.setFileId("F-88");
        when(sourceFileDbService.getById(5L)).thenReturn(sourceFile);
    }

    private void noExistingTask() {
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.STRUCTURE.name()))
                .thenReturn(null);
    }

    private void savedTaskGetsId(long id) {
        when(pipelineTaskDbService.save(any(KbPipelineTask.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbPipelineTask.class).setId(id);
            return true;
        });
    }

    @Test
    void newTaskShouldBeQueuedAndEnqueued() {
        noExistingTask();
        savedTaskGetsId(60L);

        StageTriggerVO vo = triggerSupport.trigger(10L, PipelineStage.STRUCTURE, 50L, null, "组装", false);

        assertEquals(60L, vo.getPipelineTaskId());
        ArgumentCaptor<KbPipelineTask> captor = ArgumentCaptor.forClass(KbPipelineTask.class);
        verify(pipelineTaskDbService).save(captor.capture());
        assertEquals(PipelineTaskStatus.QUEUED.name(), captor.getValue().getStatus());
        verify(taskQueue).enqueue(60L);
        verify(pipelineTaskDbService, never()).failQueued(any(), anyString(), anyString());
    }

    @Test
    void enqueueFailureShouldFailTheTaskAndRethrow() {
        noExistingTask();
        savedTaskGetsId(61L);
        doThrow(new IllegalStateException("redis down")).when(taskQueue).enqueue(61L);

        assertThrows(IllegalStateException.class,
                () -> triggerSupport.trigger(10L, PipelineStage.STRUCTURE, 50L, null, "组装", false));

        // 库里不留"排不上队"的 QUEUED 任务：状态与事实一致，用户可直接重试
        verify(pipelineTaskDbService).failQueued(61L, PipelineTaskErrorCode.TASK_ENQUEUE_FAILED.name(),
                "任务入队失败，请重试");
    }

    @Test
    void mismatchedStorageShouldReject40453BeforeAnyTaskWork() {
        stubFileChain();
        when(fileStorage.refOf("F-88")).thenReturn(new StorageRef(StorageType.MINIO.getCode(), ARCHIVE_SOURCE));
        when(storageRouter.currentSourceId()).thenReturn(CURRENT_SOURCE);
        when(storageRouter.nameOf(ARCHIVE_SOURCE)).thenReturn("演示 MinIO");
        when(storageRouter.nameOf(CURRENT_SOURCE)).thenReturn("本地磁盘");

        KnowledgeException e = assertThrows(KnowledgeException.class,
                () -> triggerSupport.trigger(10L, PipelineStage.STRUCTURE, 50L, null, "组装", false));

        assertEquals(ErrorCode.STORAGE_TYPE_MISMATCH, e.getErrorCode());
        // 文案按数据源名称给出两侧，用户能直接看出是哪两个数据源
        assertTrue(e.getMessage().contains("演示 MinIO"), e.getMessage());
        assertTrue(e.getMessage().contains("本地磁盘"), e.getMessage());
        verify(pipelineTaskDbService, never()).save(any());
        verify(taskQueue, never()).enqueue(any());
    }

    @Test
    void matchedStorageShouldPassTheGuard() {
        stubFileChain();
        when(fileStorage.refOf("F-88")).thenReturn(new StorageRef(StorageType.MINIO.getCode(), CURRENT_SOURCE));
        when(storageRouter.currentSourceId()).thenReturn(CURRENT_SOURCE);
        noExistingTask();
        savedTaskGetsId(62L);

        StageTriggerVO vo = triggerSupport.trigger(10L, PipelineStage.STRUCTURE, 50L, null, "组装", false);

        assertEquals(62L, vo.getPipelineTaskId());
        verify(taskQueue).enqueue(62L);
    }

    @Test
    void missingFileChainShouldNotBlockTrigger() {
        // 链路缺环（无文件结果）时不拦：由读取阶段按记录给出「存储后端未配置」
        when(fileResultDbService.getById(10L)).thenReturn(null);
        noExistingTask();
        savedTaskGetsId(63L);

        StageTriggerVO vo = triggerSupport.trigger(10L, PipelineStage.STRUCTURE, 50L, null, "组装", false);

        assertEquals(63L, vo.getPipelineTaskId());
    }
}
