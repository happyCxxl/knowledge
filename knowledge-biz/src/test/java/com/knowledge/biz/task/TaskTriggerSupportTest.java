package com.knowledge.biz.task;

import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.dto.response.task.StageTriggerVO;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.PipelineTaskErrorCode;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 环节任务触发单测：新建任务落库入队 / 入队失败落 FAILED 且异常继续抛出。
 *
 * @author cxxl
 */
@ExtendWith(MockitoExtension.class)
class TaskTriggerSupportTest {

    @Mock
    private KbPipelineTaskDbService pipelineTaskDbService;
    @Mock
    private TaskQueueSupport taskQueue;

    private TaskTriggerSupport triggerSupport;

    @BeforeEach
    void setUp() {
        triggerSupport = new TaskTriggerSupport(pipelineTaskDbService, taskQueue);
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
}
