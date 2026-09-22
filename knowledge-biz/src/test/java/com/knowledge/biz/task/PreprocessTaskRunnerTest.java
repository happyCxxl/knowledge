package com.knowledge.biz.task;

import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineStepLogDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.preprocess.PreprocessOutcome;
import com.knowledge.common.domain.preprocess.PreprocessView;
import com.knowledge.common.domain.structure.DocumentInfo;
import com.knowledge.common.domain.structure.UnifiedDocument;
import com.knowledge.common.domain.structure.UnifiedElement;
import com.knowledge.common.domain.task.StepLogInfo;
import com.knowledge.common.enums.structure.UnifiedElementType;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.PipelineTaskErrorCode;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.filecenter.service.FileStorage;
import com.knowledge.worker.preprocessing.PreprocessContext;
import com.knowledge.worker.preprocessing.PreprocessProperties;
import com.knowledge.worker.preprocessing.PreprocessorPort;
import com.knowledge.worker.preprocessing.strategy.PreprocessStrategyParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 预处理任务执行器单测：成功落库（upstream 链 + 9 步日志）/ 上游缺失 PREPROCESS_EMPTY / 管线失败。
 *
 * @author cxxl
 */
@ExtendWith(MockitoExtension.class)
class PreprocessTaskRunnerTest {

    @Mock
    private KbPipelineTaskDbService pipelineTaskDbService;
    @Mock
    private KbFileResultDbService fileResultDbService;
    @Mock
    private KbPipelineProductDbService pipelineProductDbService;
    @Mock
    private KbPipelineStepLogDbService stepLogDbService;
    @Mock
    private FileStorage fileStorage;
    @Mock
    private PreprocessorPort preprocessor;

    private PreprocessTaskRunner runner;

    @BeforeEach
    void setUp() {
        runner = new PreprocessTaskRunner(pipelineTaskDbService, fileResultDbService,
                pipelineProductDbService, fileStorage, preprocessor,
                new PreprocessProperties(), new PreprocessStrategyParser(new PreprocessProperties()),
                new StepLogPersistence(stepLogDbService),
                new ProductPersistence(pipelineProductDbService, pipelineTaskDbService, fileStorage));
    }

    private KbPipelineTask queuedTask() {
        KbPipelineTask task = new KbPipelineTask();
        task.setId(60L);
        task.setFileResultId(10L);
        task.setUpstreamProductId(50L);
        task.setStage(PipelineStage.PREPROCESS.name());
        task.setStatus(PipelineTaskStatus.QUEUED.name());
        return task;
    }

    private KbPipelineProduct structureProduct() {
        KbPipelineProduct product = new KbPipelineProduct();
        product.setId(50L);
        product.setArtifactId("abc".repeat(22));
        return product;
    }

    private UnifiedDocument document() {
        UnifiedDocument document = new UnifiedDocument();
        DocumentInfo info = new DocumentInfo();
        info.setDocumentId("doc-10");
        document.setDocumentInfo(info);
        List<UnifiedElement> elements = new ArrayList<>();
        UnifiedElement element = new UnifiedElement();
        element.setId("n-1");
        element.setType(UnifiedElementType.PARAGRAPH.name());
        element.setText("正文内容");
        elements.add(element);
        document.setElements(elements);
        return document;
    }

    private PreprocessOutcome successOutcome() {
        PreprocessOutcome outcome = new PreprocessOutcome();
        outcome.setSuggestedStatus(PipelineTaskStatus.SUCCESS.name());
        PreprocessView view = new PreprocessView();
        view.setViewId("pv-doc-10-preproc-default-v1");
        outcome.setView(view);
        List<StepLogInfo> steps = new ArrayList<>();
        for (String name : List.of("编码规范化", "段落/表格文本整理", "页眉页脚处置", "目录处置",
                "重复处置", "字段规范化", "噪声处置", "自定义规则", "视图组装")) {
            StepLogInfo step = new StepLogInfo();
            step.setStepName(name);
            step.setStatus("SUCCESS");
            step.setAttemptCount(1);
            step.setWarningCount(0);
            step.setMatchedCount(1);
            step.setChangedCount(0);
            steps.add(step);
        }
        outcome.setStepLogs(steps);
        return outcome;
    }

    private KbFileResult fileResult() {
        KbFileResult fileResult = new KbFileResult();
        fileResult.setId(10L);
        fileResult.setSourceFileId(5L);
        return fileResult;
    }

    @Test
    void successPathShouldPersistViewProductWithUpstreamChainAndNineSteps() {
        when(pipelineTaskDbService.getById(60L)).thenReturn(queuedTask());
        when(pipelineTaskDbService.claim(60L)).thenReturn(1);
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        when(pipelineProductDbService.getById(50L)).thenReturn(structureProduct());
        when(fileStorage.getObject("abc".repeat(22)))
                .thenReturn(Objects.requireNonNull(JsonUtil.toJsonStr(document())).getBytes(StandardCharsets.UTF_8));
        when(preprocessor.preprocess(any(PreprocessContext.class))).thenReturn(successOutcome());
        when(fileStorage.putObject(any(byte[].class))).thenReturn("def".repeat(21) + "0");
        when(pipelineProductDbService.save(any(KbPipelineProduct.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbPipelineProduct.class).setId(70L);
            return true;
        });

        runner.run(60L);

        ArgumentCaptor<PreprocessContext> contextCaptor = ArgumentCaptor.forClass(PreprocessContext.class);
        verify(preprocessor).preprocess(contextCaptor.capture());
        assertEquals("doc-10", contextCaptor.getValue().getDocument().getDocumentInfo().getDocumentId());
        assertNotNull(contextCaptor.getValue().getStrategy());

        ArgumentCaptor<KbPipelineProduct> productCaptor = ArgumentCaptor.forClass(KbPipelineProduct.class);
        verify(pipelineProductDbService).save(productCaptor.capture());
        assertEquals(PipelineStage.PREPROCESS.name(), productCaptor.getValue().getStage());
        assertEquals(50L, productCaptor.getValue().getUpstreamProductId());
        assertEquals(10L, productCaptor.getValue().getFileResultId());
        assertNotNull(productCaptor.getValue().getCapabilitySnapshot());

        verify(pipelineTaskDbService).finish(60L, PipelineTaskStatus.SUCCESS.name(), null, null);
        verify(pipelineTaskDbService).updateProductId(60L, 70L);
        verify(stepLogDbService, times(9)).save(any());
    }

    @Test
    void specifiedUpstreamProductShouldBePreferred() {
        KbPipelineTask task = queuedTask();
        task.setUpstreamProductId(88L);
        when(pipelineTaskDbService.getById(60L)).thenReturn(task);
        when(pipelineTaskDbService.claim(60L)).thenReturn(1);
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        KbPipelineProduct specified = structureProduct();
        specified.setId(88L);
        specified.setArtifactId("spec".repeat(16));
        when(pipelineProductDbService.getById(88L)).thenReturn(specified);
        when(fileStorage.getObject("spec".repeat(16)))
                .thenReturn(Objects.requireNonNull(JsonUtil.toJsonStr(document())).getBytes(StandardCharsets.UTF_8));
        when(preprocessor.preprocess(any(PreprocessContext.class))).thenReturn(successOutcome());
        when(fileStorage.putObject(any(byte[].class))).thenReturn("out".repeat(16));
        when(pipelineProductDbService.save(any(KbPipelineProduct.class))).thenAnswer(inv -> {
            inv.getArgument(0, KbPipelineProduct.class).setId(70L);
            return true;
        });

        runner.run(60L);

        verify(fileStorage).getObject("spec".repeat(16));
        verify(pipelineProductDbService, never()).getByFileResultIdAndStage(any(), any());
        ArgumentCaptor<KbPipelineProduct> productCaptor = ArgumentCaptor.forClass(KbPipelineProduct.class);
        verify(pipelineProductDbService).save(productCaptor.capture());
        assertEquals(88L, productCaptor.getValue().getUpstreamProductId());
    }

    @Test
    void missingStructureProductShouldFailWithPreprocessEmpty() {
        when(pipelineTaskDbService.getById(60L)).thenReturn(queuedTask());
        when(pipelineTaskDbService.claim(60L)).thenReturn(1);
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        when(pipelineProductDbService.getById(50L)).thenReturn(null);
        when(pipelineProductDbService.getByFileResultIdAndStage(10L, PipelineStage.STRUCTURE.name()))
                .thenReturn(null);

        runner.run(60L);

        verify(pipelineTaskDbService).finish(eq(60L), eq(PipelineTaskStatus.FAILED.name()),
                eq(PipelineTaskErrorCode.PREPROCESS_EMPTY.name()), any());
        verify(fileStorage, never()).putObject(any(byte[].class));
    }

    @Test
    void failedOutcomeShouldFinishFailedWithoutProduct() {
        when(pipelineTaskDbService.getById(60L)).thenReturn(queuedTask());
        when(pipelineTaskDbService.claim(60L)).thenReturn(1);
        when(fileResultDbService.getById(10L)).thenReturn(fileResult());
        when(pipelineProductDbService.getById(50L)).thenReturn(structureProduct());
        when(fileStorage.getObject("abc".repeat(22)))
                .thenReturn(Objects.requireNonNull(JsonUtil.toJsonStr(document())).getBytes(StandardCharsets.UTF_8));
        PreprocessOutcome outcome = new PreprocessOutcome();
        outcome.setSuggestedStatus(PipelineTaskStatus.FAILED.name());
        outcome.setErrorCode(PipelineTaskErrorCode.PREPROCESS_EMPTY.name());
        outcome.setErrorMsg("上游统一结构无任何可处理元素");
        when(preprocessor.preprocess(any(PreprocessContext.class))).thenReturn(outcome);

        runner.run(60L);

        verify(pipelineTaskDbService).finish(eq(60L), eq(PipelineTaskStatus.FAILED.name()),
                eq(PipelineTaskErrorCode.PREPROCESS_EMPTY.name()), any());
        verify(fileStorage, never()).putObject(any(byte[].class));
        verify(pipelineProductDbService, never()).save(any());
    }
}
