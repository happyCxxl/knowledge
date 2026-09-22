package com.knowledge.biz.task;

import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineStepLogDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.parse.ParseResult;
import com.knowledge.common.domain.structure.AssembleOutcome;
import com.knowledge.common.domain.structure.UnifiedDocument;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.PipelineTaskErrorCode;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.utils.JsonUtil;
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

    @Mock
    private KbPipelineTaskDbService pipelineTaskDbService;
    @Mock
    private KbPipelineProductDbService pipelineProductDbService;
    @Mock
    private KbPipelineStepLogDbService stepLogDbService;
    @Mock
    private FileStorage fileStorage;
    @Mock
    private DocumentAssemblerPort documentAssembler;

    private StructureTaskRunner runner;

    @BeforeEach
    void setUp() {
        runner = new StructureTaskRunner(pipelineTaskDbService, pipelineProductDbService,
                fileStorage, documentAssembler, new StructureProperties(),
                new StepLogPersistence(stepLogDbService),
                new ProductPersistence(pipelineProductDbService, pipelineTaskDbService, fileStorage));
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
        return product;
    }

    private AssembleOutcome successOutcome() {
        AssembleOutcome outcome = new AssembleOutcome();
        outcome.setSuggestedStatus(PipelineTaskStatus.SUCCESS.name());
        outcome.setDocument(new UnifiedDocument());
        return outcome;
    }

    @Test
    void successPathShouldPersistStructureProductWithUpstreamChain() {
        when(pipelineTaskDbService.getById(60L)).thenReturn(queuedTask());
        when(pipelineTaskDbService.claim(60L)).thenReturn(1);
        when(pipelineProductDbService.getById(50L)).thenReturn(parseProduct());
        when(fileStorage.getObject("abc".repeat(22)))
                .thenReturn(JsonUtil.toJsonStr(new ParseResult()).getBytes(StandardCharsets.UTF_8));
        when(documentAssembler.assemble(any(ParseResult.class), any(AssembleContext.class)))
                .thenReturn(successOutcome());
        when(fileStorage.putObject(any(byte[].class))).thenReturn("def".repeat(21) + "0");
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
        verify(pipelineTaskDbService).finish(60L, PipelineTaskStatus.SUCCESS.name(), null, null);
        verify(pipelineTaskDbService).updateProductId(60L, 70L);
    }

    @Test
    void emptyTreeShouldFailWithoutProduct() {
        when(pipelineTaskDbService.getById(60L)).thenReturn(queuedTask());
        when(pipelineTaskDbService.claim(60L)).thenReturn(1);
        when(pipelineProductDbService.getById(50L)).thenReturn(parseProduct());
        when(fileStorage.getObject("abc".repeat(22)))
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
    void missingUpstreamProductShouldFailStructureEmpty() {
        when(pipelineTaskDbService.getById(60L)).thenReturn(queuedTask());
        when(pipelineTaskDbService.claim(60L)).thenReturn(1);
        when(pipelineProductDbService.getById(50L)).thenReturn(null);
        when(pipelineProductDbService.getByFileResultIdAndStage(10L, PipelineStage.PARSE.name())).thenReturn(null);

        runner.run(60L);

        verify(pipelineTaskDbService).finish(eq(60L), eq(PipelineTaskStatus.FAILED.name()),
                eq(PipelineTaskErrorCode.STRUCTURE_EMPTY.name()), any());
        verify(fileStorage, never()).getObject(any());
    }
}
