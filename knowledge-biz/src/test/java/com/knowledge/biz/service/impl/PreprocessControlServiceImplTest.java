package com.knowledge.biz.service.impl;

import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineStepLogDbService;
import com.knowledge.biz.service.db.KbPipelineStrategyVersionDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.biz.service.db.KbStrategyBindingDbService;
import com.knowledge.biz.service.db.KnowledgeBaseDbService;
import com.knowledge.biz.service.support.PreprocessVoAssembler;
import com.knowledge.biz.service.support.TaskDetailSupport;
import com.knowledge.biz.task.TaskQueueSupport;
import com.knowledge.biz.task.TaskTriggerSupport;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineStepLog;
import com.knowledge.common.domain.entity.KbPipelineStrategyVersion;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.entity.KbStrategyBinding;
import com.knowledge.common.domain.entity.KnowledgeBase;
import com.knowledge.common.dto.response.preprocess.PreprocessDetailVO;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import com.knowledge.filecenter.service.FileStorage;
import com.knowledge.worker.preprocessing.PreprocessProperties;
import com.knowledge.worker.preprocessing.strategy.PreprocessStrategy;
import com.knowledge.worker.preprocessing.strategy.PreprocessStrategyParser;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预处理控制面服务单测：策略解析四档（显式/KB 绑定/最新启用/内置默认）/ 上游产物校验 / 防重复用 / 建任务快照入队 / 预处理详情。
 *
 * @author cxxl
 */
@ExtendWith(MockitoExtension.class)
class PreprocessControlServiceImplTest {

    @Mock
    private KbFileResultDbService fileResultDbService;
    @Mock
    private KbPipelineTaskDbService pipelineTaskDbService;
    @Mock
    private KbPipelineProductDbService pipelineProductDbService;
    @Mock
    private KbPipelineStrategyVersionDbService strategyVersionDbService;
    @Mock
    private KbPipelineStepLogDbService stepLogDbService;
    @Mock
    private TaskQueueSupport taskQueue;
    @Mock
    private FileStorage fileStorage;
    @Mock
    private KbStrategyBindingDbService strategyBindingDbService;
    @Mock
    private KnowledgeBaseDbService knowledgeBaseDbService;

    private PreprocessControlServiceImpl service;

    @BeforeEach
    void setUp() {
        // 触发/详情助手为纯委托类、组装器为纯映射类，用真实实例（mock 会让 VO 组装返回 null，断言失真）
        service = new PreprocessControlServiceImpl(fileResultDbService, pipelineProductDbService,
                stepLogDbService, strategyVersionDbService, strategyBindingDbService, knowledgeBaseDbService,
                new TaskTriggerSupport(pipelineTaskDbService, taskQueue),
                new TaskDetailSupport(pipelineTaskDbService), fileStorage,
                new PreprocessStrategyParser(new PreprocessProperties()),
                new PreprocessVoAssembler());
    }

    private KbFileResult fileResult() {
        KbFileResult fileResult = new KbFileResult();
        fileResult.setId(10L);
        fileResult.setKnowledgeBaseId(10L);
        return fileResult;
    }

    private KbPipelineProduct structureProduct() {
        KbPipelineProduct product = new KbPipelineProduct();
        product.setId(50L);
        return product;
    }

    private void stubCommon() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        when(pipelineProductDbService.getByFileResultIdAndStage(10L, PipelineStage.STRUCTURE.name()))
                .thenReturn(structureProduct());
    }

    /** 默认知识库（绑定开关未显式关闭 → 视为开启） */
    private KnowledgeBase knowledgeBase() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(10L);
        kb.setStrategyBindingEnabled(1);
        return kb;
    }

    /** 有效绑定行：KB 10 绑定 PREPROCESS 策略版本 66 */
    private KbStrategyBinding binding() {
        KbStrategyBinding binding = new KbStrategyBinding();
        binding.setId(1L);
        binding.setKnowledgeBaseId(10L);
        binding.setStrategyType(PreprocessStrategy.TYPE);
        binding.setStrategyVersionId(66L);
        return binding;
    }

    @Test
    void shouldCreateTaskWithDefaultStrategySnapshotAndEnqueue() {
        stubCommon();
        when(strategyVersionDbService.getLatestEnabledByType(PreprocessStrategy.TYPE)).thenReturn(null);
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.PREPROCESS.name()))
                .thenReturn(null);
        when(pipelineTaskDbService.save(any(KbPipelineTask.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbPipelineTask.class).setId(31L);
            return true;
        });

        var response = service.preprocess(10L, null, null);

        assertEquals(10L, response.getFileResultId());
        assertEquals(31L, response.getPipelineTaskId());
        assertEquals("preproc-default-v1", response.getStrategyVersion());
        ArgumentCaptor<KbPipelineTask> captor = ArgumentCaptor.forClass(KbPipelineTask.class);
        verify(pipelineTaskDbService).save(captor.capture());
        KbPipelineTask task = captor.getValue();
        assertEquals(PipelineStage.PREPROCESS.name(), task.getStage());
        assertEquals(PipelineTaskStatus.QUEUED.name(), task.getStatus());
        assertEquals(50L, task.getUpstreamProductId());
        assertNotNull(task.getStrategySnapshot());
        assertTrue(task.getStrategySnapshot().contains("headerFooter"));
        verify(taskQueue).enqueue(31L);
    }

    @Test
    void latestEnabledStrategyShouldResolveFromDb() {
        stubCommon();
        KbPipelineStrategyVersion row = new KbPipelineStrategyVersion();
        row.setId(1L);
        row.setType(PreprocessStrategy.TYPE);
        row.setName("preproc-strict");
        row.setVersion("v2");
        row.setConfigSnapshot("{\"rules\":{\"headerFooter\":{\"action\":\"EXCLUDE\"}}}");
        when(strategyVersionDbService.getLatestEnabledByType(PreprocessStrategy.TYPE)).thenReturn(row);
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.PREPROCESS.name()))
                .thenReturn(null);
        when(pipelineTaskDbService.save(any(KbPipelineTask.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbPipelineTask.class).setId(32L);
            return true;
        });

        var response = service.preprocess(10L, null, null);

        assertEquals("preproc-strict-v2", response.getStrategyVersion());
        ArgumentCaptor<KbPipelineTask> captor = ArgumentCaptor.forClass(KbPipelineTask.class);
        verify(pipelineTaskDbService).save(captor.capture());
        assertTrue(captor.getValue().getStrategySnapshot().contains("EXCLUDE"));
    }

    @Test
    void explicitStrategyVersionShouldResolveAndValidate() {
        stubCommon();
        KbPipelineStrategyVersion row = new KbPipelineStrategyVersion();
        row.setType(PreprocessStrategy.TYPE);
        row.setName("preproc-strict");
        row.setVersion("v2");
        row.setStatus("ACTIVE");
        row.setConfigSnapshot("{\"rules\":{\"toc\":{\"action\":\"EXCLUDE\"}}}");
        when(strategyVersionDbService.getById(66L)).thenReturn(row);
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.PREPROCESS.name()))
                .thenReturn(null);
        when(pipelineTaskDbService.save(any(KbPipelineTask.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbPipelineTask.class).setId(33L);
            return true;
        });

        var response = service.preprocess(10L, 66L, null);

        assertEquals("preproc-strict-v2", response.getStrategyVersion());
        verify(strategyVersionDbService).getById(eq(66L));
        verify(strategyVersionDbService, never()).getLatestEnabledByType(any());
    }

    @Test
    void boundStrategyShouldBeUsedWhenNoExplicitVersion() {
        stubCommon();
        when(knowledgeBaseDbService.getActiveById(10L)).thenReturn(knowledgeBase());
        when(strategyBindingDbService.getByKbAndType(10L, PreprocessStrategy.TYPE)).thenReturn(binding());
        KbPipelineStrategyVersion bound = new KbPipelineStrategyVersion();
        bound.setId(66L);
        bound.setType(PreprocessStrategy.TYPE);
        bound.setName("preproc-bound");
        bound.setVersion("v3");
        bound.setStatus("ACTIVE");
        bound.setConfigSnapshot("{\"rules\":{\"headerFooter\":{\"action\":\"KEEP\"}}}");
        when(strategyVersionDbService.getById(66L)).thenReturn(bound);
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.PREPROCESS.name()))
                .thenReturn(null);
        when(pipelineTaskDbService.save(any(KbPipelineTask.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbPipelineTask.class).setId(34L);
            return true;
        });

        var response = service.preprocess(10L, null, null);

        assertEquals("preproc-bound-v3", response.getStrategyVersion());
        // 绑定生效时不查全局最新启用
        verify(strategyVersionDbService, never()).getLatestEnabledByType(any());
    }

    @Test
    void explicitStrategyShouldOverrideBinding() {
        stubCommon();
        KbPipelineStrategyVersion row = new KbPipelineStrategyVersion();
        row.setType(PreprocessStrategy.TYPE);
        row.setName("preproc-explicit");
        row.setVersion("v4");
        row.setStatus("ACTIVE");
        row.setConfigSnapshot("{\"rules\":{\"headerFooter\":{\"action\":\"EXCLUDE\"}}}");
        when(strategyVersionDbService.getById(66L)).thenReturn(row);
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.PREPROCESS.name()))
                .thenReturn(null);
        when(pipelineTaskDbService.save(any(KbPipelineTask.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbPipelineTask.class).setId(35L);
            return true;
        });

        var response = service.preprocess(10L, 66L, null);

        assertEquals("preproc-explicit-v4", response.getStrategyVersion());
        // 显式指定时不经绑定档与全局最新档
        verify(strategyBindingDbService, never()).getByKbAndType(any(), any());
        verify(strategyVersionDbService, never()).getLatestEnabledByType(any());
    }

    @Test
    void staleBindingShouldFallbackToLatestEnabled() {
        // 绑定行指向的策略版本行缺失 → 失效回退全局最新启用
        stubCommon();
        when(knowledgeBaseDbService.getActiveById(10L)).thenReturn(knowledgeBase());
        when(strategyBindingDbService.getByKbAndType(10L, PreprocessStrategy.TYPE)).thenReturn(binding());
        when(strategyVersionDbService.getById(66L)).thenReturn(null);
        KbPipelineStrategyVersion latest = new KbPipelineStrategyVersion();
        latest.setId(2L);
        latest.setType(PreprocessStrategy.TYPE);
        latest.setName("preproc-latest");
        latest.setVersion("v5");
        latest.setStatus("ACTIVE");
        latest.setConfigSnapshot("{\"rules\":{\"headerFooter\":{\"action\":\"MARK\"}}}");
        when(strategyVersionDbService.getLatestEnabledByType(PreprocessStrategy.TYPE)).thenReturn(latest);
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.PREPROCESS.name()))
                .thenReturn(null);
        when(pipelineTaskDbService.save(any(KbPipelineTask.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbPipelineTask.class).setId(36L);
            return true;
        });

        var response = service.preprocess(10L, null, null);

        assertEquals("preproc-latest-v5", response.getStrategyVersion());
    }

    @Test
    void unknownStrategyVersionShouldThrow40433() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        when(strategyVersionDbService.getById(999L)).thenReturn(null);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.preprocess(10L, 999L, null));
        assertEquals(ErrorCode.STRATEGY_VERSION_NOT_FOUND, e.getErrorCode());
        verify(pipelineTaskDbService, never()).save(any());
        verify(pipelineProductDbService, never()).getByFileResultIdAndStage(any(), any());
    }

    @Test
    void missingStructureProductShouldThrow40432() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        when(strategyVersionDbService.getLatestEnabledByType(PreprocessStrategy.TYPE)).thenReturn(null);
        when(pipelineProductDbService.getByFileResultIdAndStage(10L, PipelineStage.STRUCTURE.name()))
                .thenReturn(null);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.preprocess(10L, null, null));
        assertEquals(ErrorCode.FILE_RESULT_NOT_FOUND, e.getErrorCode());
        verify(pipelineTaskDbService, never()).save(any());
        verify(taskQueue, never()).enqueue(any());
    }

    @Test
    void runningShouldReject40431() {
        stubCommon();
        when(strategyVersionDbService.getLatestEnabledByType(PreprocessStrategy.TYPE)).thenReturn(null);
        KbPipelineTask running = new KbPipelineTask();
        running.setId(31L);
        running.setStatus(PipelineTaskStatus.RUNNING.name());
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.PREPROCESS.name()))
                .thenReturn(running);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.preprocess(10L, null, null));
        assertEquals(ErrorCode.TASK_ALREADY_PENDING, e.getErrorCode());
        verify(pipelineTaskDbService, never()).save(any());
        verify(taskQueue, never()).enqueue(any());
    }

    @Test
    void queuedShouldReuseExistingTask() {
        stubCommon();
        when(strategyVersionDbService.getLatestEnabledByType(PreprocessStrategy.TYPE)).thenReturn(null);
        KbPipelineTask queued = new KbPipelineTask();
        queued.setId(31L);
        queued.setStatus(PipelineTaskStatus.QUEUED.name());
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.PREPROCESS.name()))
                .thenReturn(queued);

        var response = service.preprocess(10L, null, null);

        assertEquals(31L, response.getPipelineTaskId());
        verify(pipelineTaskDbService, never()).save(any());
        verify(taskQueue).enqueue(31L);
    }

    @Test
    void specifiedUpstreamProductShouldBeStoredInTask() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        when(strategyVersionDbService.getLatestEnabledByType(PreprocessStrategy.TYPE)).thenReturn(null);
        KbPipelineProduct specified = new KbPipelineProduct();
        specified.setId(88L);
        specified.setFileResultId(10L);
        specified.setStage(PipelineStage.STRUCTURE.name());
        when(pipelineProductDbService.getById(88L)).thenReturn(specified);
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.PREPROCESS.name()))
                .thenReturn(null);
        when(pipelineTaskDbService.save(any(KbPipelineTask.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbPipelineTask.class).setId(34L);
            return true;
        });

        var response = service.preprocess(10L, null, 88L);

        ArgumentCaptor<KbPipelineTask> captor = ArgumentCaptor.forClass(KbPipelineTask.class);
        verify(pipelineTaskDbService).save(captor.capture());
        assertEquals(88L, captor.getValue().getUpstreamProductId());
        assertEquals(34L, response.getPipelineTaskId());
    }

    @Test
    void detailShouldAssembleTaskStepsStrategyAndElements() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        KbPipelineTask task = new KbPipelineTask();
        task.setId(31L);
        task.setStage(PipelineStage.PREPROCESS.name());
        task.setStatus(PipelineTaskStatus.SUCCESS.name());
        task.setProductId(50L);
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.PREPROCESS.name())).thenReturn(task);
        KbPipelineStepLog step = new KbPipelineStepLog();
        step.setStepName("字段标准化");
        step.setStatus("SUCCESS");
        step.setMatchedCount(3);
        step.setChangedCount(2);
        when(stepLogDbService.listByTaskId(31L)).thenReturn(List.of(step));
        KbPipelineProduct product = new KbPipelineProduct();
        product.setArtifactId("abc".repeat(16));
        when(pipelineProductDbService.getById(50L)).thenReturn(product);
        String json = "{\"strategyVersion\":\"preproc-default-v1\","
                + "\"options\":{\"pageHeaderFooter\":\"MARK\"},"
                + "\"elements\":["
                + "{\"elementId\":\"h-1\",\"type\":\"TITLE\",\"status\":\"NORMAL\",\"displayText\":\"第一章\",\"normalizedText\":\"第一章\"},"
                + "{\"elementId\":\"n-1\",\"type\":\"PARAGRAPH\",\"status\":\"NORMAL\",\"rawText\":\"金额 叁佰万 元整\","
                + "\"displayText\":\"金额 叁佰万 元整\","
                + "\"normalizedText\":\"金额 3000000 元整\",\"normalizedFields\":[{\"field\":\"AMOUNT\",\"value\":\"3000000.00\",\"unit\":\"元\",\"rule\":\"amount-cn-v1\"}],"
                + "\"preprocessTrace\":[{\"rule\":\"amount-cn-v1\",\"field\":\"AMOUNT\",\"action\":\"EXTRACT\",\"before\":\"叁佰万\",\"after\":\"3000000.00\",\"evidence\":\"中文金额命中\"}]},"
                + "{\"elementId\":\"hdr-1\",\"type\":\"HEADER\",\"status\":\"EXCLUDED_HEADER\",\"rawText\":\"页眉文本\",\"displayText\":\"页眉文本\","
                + "\"preprocessTrace\":[{\"rule\":\"header-footer-detect-v1\",\"action\":\"EXCLUDE\",\"before\":\"页眉文本\",\"evidence\":\"多页重复页眉\"}]}]}";
        when(fileStorage.getObject("abc".repeat(16))).thenReturn(json.getBytes(StandardCharsets.UTF_8));

        PreprocessDetailVO detail = service.preprocessDetail(10L, null);

        assertEquals(31L, detail.getTaskId());
        assertEquals(PipelineTaskStatus.SUCCESS.name(), detail.getStatus());
        assertEquals(1, detail.getSteps().size());
        assertEquals(3, detail.getSteps().getFirst().getMatchedCount());
        assertNotNull(detail.getSummary());
        assertEquals(3, detail.getSummary().getElementCount());
        assertEquals(1, detail.getSummary().getChangedCount());
        assertEquals(1, detail.getSummary().getExcludedCount());
        assertEquals(1, detail.getSummary().getFieldCount());
        assertEquals(3, detail.getElements().size());
        assertEquals(1, detail.getElements().get(1).getFields().size());
        assertEquals("AMOUNT", detail.getElements().get(1).getFields().getFirst().getField());
        // 原文透传 + 处理轨迹透传（非 KEEP 条目）
        assertEquals("金额 叁佰万 元整", detail.getElements().get(1).getRawText());
        assertEquals(1, detail.getElements().get(1).getTrace().size());
        assertEquals("EXTRACT", detail.getElements().get(1).getTrace().getFirst().getAction());
        assertEquals("amount-cn-v1", detail.getElements().get(1).getTrace().getFirst().getRule());
        assertEquals("EXCLUDE", detail.getElements().get(2).getTrace().getFirst().getAction());
        assertEquals("多页重复页眉", detail.getElements().get(2).getTrace().getFirst().getEvidence());
    }

    @Test
    void detailWithoutTaskShouldReturnEmptyElements() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        when(pipelineTaskDbService.getByFileResultIdAndStage(10L, PipelineStage.PREPROCESS.name())).thenReturn(null);

        PreprocessDetailVO detail = service.preprocessDetail(10L, null);

        assertNotNull(detail.getElements());
        assertTrue(detail.getElements().isEmpty());
    }

    @Test
    void detailWithWrongStageTaskShouldReject() {
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        KbPipelineTask structureTask = new KbPipelineTask();
        structureTask.setId(51L);
        structureTask.setFileResultId(10L);
        structureTask.setStage(PipelineStage.STRUCTURE.name());
        when(pipelineTaskDbService.getById(51L)).thenReturn(structureTask);

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> service.preprocessDetail(10L, 51L));
        assertEquals(ErrorCode.PARAM_INVALID, e.getErrorCode());
    }
}
