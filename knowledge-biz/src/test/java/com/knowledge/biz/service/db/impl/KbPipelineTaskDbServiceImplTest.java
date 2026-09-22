package com.knowledge.biz.service.db.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.knowledge.biz.mapper.KbPipelineTaskMapper;
import com.knowledge.common.domain.entity.KbPipelineTask;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 任务数据访问单测：过期 QUEUED / 孤儿 RUNNING 查询条件与终态回写条件。
 *
 * @author cxxl
 */
@ExtendWith(MockitoExtension.class)
class KbPipelineTaskDbServiceImplTest {

    @Mock
    private KbPipelineTaskMapper mapper;

    private KbPipelineTaskDbServiceImpl impl;

    @BeforeEach
    void setUp() {
        impl = new KbPipelineTaskDbServiceImpl();
        ReflectionTestUtils.setField(impl, "baseMapper", mapper);
        // 初始化实体列缓存（LambdaWrapper 解析列名依赖 TableInfo）
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"),
                KbPipelineTask.class);
    }

    @Test
    void listStaleQueuedShouldFilterQueuedBeforeThreshold() {
        KbPipelineTask task = new KbPipelineTask();
        task.setId(1L);
        when(mapper.selectList(any())).thenReturn(List.of(task));
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);

        List<KbPipelineTask> result = impl.listStaleQueued(threshold);

        assertEquals(1, result.size());
        verify(mapper).selectList(argThat(wrapper -> {
            AbstractWrapper<?, ?, ?> w = (AbstractWrapper<?, ?, ?>) wrapper;
            assertTrue(w.getSqlSegment().contains("status"));
            assertTrue(w.getSqlSegment().contains("create_time"));
            assertTrue(w.getParamNameValuePairs().containsValue("QUEUED"));
            return true;
        }));
    }

    @Test
    void listStaleRunningShouldFilterRunningBeforeThreshold() {
        KbPipelineTask task = new KbPipelineTask();
        task.setId(2L);
        when(mapper.selectList(any())).thenReturn(List.of(task));
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);

        List<KbPipelineTask> result = impl.listStaleRunning(threshold);

        assertEquals(1, result.size());
        verify(mapper).selectList(argThat(wrapper -> {
            AbstractWrapper<?, ?, ?> w = (AbstractWrapper<?, ?, ?>) wrapper;
            assertTrue(w.getSqlSegment().contains("status"));
            assertTrue(w.getSqlSegment().contains("started_at"));
            assertTrue(w.getParamNameValuePairs().containsValue("RUNNING"));
            return true;
        }));
    }

    @Test
    void finishShouldConditionallyUpdateRunningToTerminal() {
        when(mapper.update(isNull(), any())).thenReturn(1);

        int rows = impl.finish(9L, "FAILED", "EXECUTOR_TIMEOUT", "超时");

        assertEquals(1, rows);
        verify(mapper).update(isNull(), argThat(wrapper -> {
            AbstractWrapper<?, ?, ?> w = (AbstractWrapper<?, ?, ?>) wrapper;
            assertTrue(w.getSqlSegment().contains("status"));
            assertTrue(w.getSqlSet().contains("status"));
            assertTrue(w.getSqlSet().contains("error_code"));
            assertTrue(w.getSqlSet().contains("finished_at"));
            assertTrue(w.getParamNameValuePairs().containsValue("RUNNING"));
            assertTrue(w.getParamNameValuePairs().containsValue("FAILED"));
            return true;
        }));
    }
}
