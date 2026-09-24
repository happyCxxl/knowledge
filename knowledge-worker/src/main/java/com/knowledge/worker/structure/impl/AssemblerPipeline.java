package com.knowledge.worker.structure.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.common.domain.parse.PageDimension;
import com.knowledge.common.domain.parse.ParseResult;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.domain.parse.QualityWarning;
import com.knowledge.common.domain.structure.*;
import com.knowledge.common.domain.task.StepLogInfo;
import com.knowledge.common.enums.parse.QualityWarningCode;
import com.knowledge.common.enums.task.PipelineTaskErrorCode;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.enums.task.StepStatus;
import com.knowledge.worker.pipeline.StepLogHelper;
import com.knowledge.worker.structure.AssembleContext;
import com.knowledge.worker.structure.DocumentAssemblerPort;
import com.knowledge.worker.structure.craft.ContinuationOutcome;
import com.knowledge.worker.structure.craft.DedupMerger;
import com.knowledge.worker.structure.craft.ElementNormalizer;
import com.knowledge.worker.structure.craft.MarkOutcome;
import com.knowledge.worker.structure.craft.MergeOutcome;
import com.knowledge.worker.structure.craft.ReadingOrderResolver;
import com.knowledge.worker.structure.craft.RepeatNoiseMarker;
import com.knowledge.worker.structure.craft.StructureAssembler;
import com.knowledge.worker.structure.craft.TableContinuationResolver;
import com.knowledge.worker.structure.craft.TreeOutcome;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 组装编排（模板方法骨架）：标准化 → 去重合并 → 阅读顺序 → 结构组装 → 跨页接续 →
 * 溯源校验 → UnifiedDocument 构建 → 完整/部分树判定。
 * 纯算法，不碰 DB/产物存储（落库回写由 biz StructureTaskRunner 编排）。
 *
 * @author cxxl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AssemblerPipeline implements DocumentAssemblerPort {

    /** 页面尺寸单位（落 UnifiedPage.unit） */
    private static final String PAGE_UNIT = "pt";

    /** 页面坐标原点（落 UnifiedPage.origin） */
    private static final String PAGE_ORIGIN = "top-left";

    private final ElementNormalizer normalizer;
    private final DedupMerger merger;
    private final ReadingOrderResolver readingOrderResolver;
    private final StructureAssembler structureAssembler;
    private final TableContinuationResolver continuationResolver;
    private final RepeatNoiseMarker repeatNoiseMarker;

    @Override
    public AssembleOutcome assemble(ParseResult parseResult, AssembleContext context) {
        AssembleOutcome outcome = new AssembleOutcome();
        if (ObjectUtil.isNull(parseResult) || ObjectUtil.isNull(parseResult.getFile())) {
            outcome.fail(PipelineTaskErrorCode.STRUCTURE_EMPTY.name(), "上游解析产物缺失");
            return outcome;
        }
        context.setSourceFileType(parseResult.getFile().getMimeType());
        log.info("===> AssemblerPipeline 开始组装, taskId={}, fileResultId={}, mimeType={}",
                context.getTaskId(), context.getFileResultId(), context.getSourceFileType());

        // ① 元素标准化
        StepLogInfo normalizeLog = StepLogHelper.begin("元素标准化");
        List<UnifiedElement> normalized = normalizer.normalize(parseResult.getSources(), context);
        normalizeLog.setStatus(StepStatus.SUCCESS.name());
        StepLogHelper.finish(normalizeLog);
        outcome.getStepLogs().add(normalizeLog);

        // ② 去重与合并 + ③ 阅读顺序
        StepLogInfo orderLog = StepLogHelper.begin("去重与阅读顺序");
        MergeOutcome merged = merger.merge(normalized, context);
        List<UnifiedElement> ordered = readingOrderResolver.resolve(merged.getElements(), context);
        orderLog.setStatus(StepStatus.SUCCESS.name());
        StepLogHelper.finish(orderLog);
        outcome.getStepLogs().add(orderLog);

        // ④ 结构组装 + ⑤ 跨页接续
        StepLogInfo assembleLog = StepLogHelper.begin("结构组装与接续");
        TreeOutcome tree = structureAssembler.assembleTree(ordered, context);
        ContinuationOutcome continuation = continuationResolver.joinContinuations(tree.getElements(), context);
        assembleLog.setStatus(StepStatus.SUCCESS.name());
        StepLogHelper.finish(assembleLog);
        outcome.getStepLogs().add(assembleLog);

        // ⑥ 溯源校验 + 组装报告
        StepLogInfo verifyLog = StepLogHelper.begin("溯源校验");
        AssembleReport report = buildReport(merged, tree, continuation);
        verifyProvenance(continuation.getElements(), report);
        verifyLog.setStatus(StepStatus.SUCCESS.name());
        StepLogHelper.finish(verifyLog);
        outcome.getStepLogs().add(verifyLog);

        // 组装文档 + 质量
        UnifiedDocument document = buildDocument(parseResult, context, continuation.getElements(),
                mergeRelations(tree.getRelations(), continuation.getRelations()));

        // 重复与噪声识别（组装环节识别写标记，处置环节读取）
        StepLogInfo markLog = StepLogHelper.begin("重复与噪声识别");
        MarkOutcome markOutcome = repeatNoiseMarker.mark(document);
        markLog.setStatus(StepStatus.SUCCESS.name());
        markLog.setWarningCount(markOutcome.getNoisePageCount());
        StepLogHelper.finish(markLog);
        outcome.getStepLogs().add(markLog);
        report.setRepeatPageCount(markOutcome.getRepeatPageCount());
        report.setRepeatSegmentCount(markOutcome.getRepeatSegmentCount());
        report.setNoisePageCount(markOutcome.getNoisePageCount());

        document.setQuality(buildQuality(merged, tree, continuation, report));
        outcome.setDocument(document);
        outcome.setReport(report);

        // ⑦ 完整/空树判定（组装不产出 PARTIAL_SUCCESS：无法挂树元素字段自上线起恒空，无消费方）
        if (document.getElements().isEmpty()) {
            outcome.fail(PipelineTaskErrorCode.STRUCTURE_EMPTY.name(), "无任何可组装元素（空树）");
        } else {
            outcome.setSuggestedStatus(PipelineTaskStatus.SUCCESS.name());
        }
        log.info("===> AssemblerPipeline 组装完成, taskId={}, fileResultId={}, elements={}, status={}, warningCount={}",
                context.getTaskId(), context.getFileResultId(), document.getElements().size(),
                outcome.getSuggestedStatus(), document.getQuality().getWarnings().size());
        for (QualityWarning warning : document.getQuality().getWarnings()) {
            log.warn("===> AssemblerPipeline 组装告警, taskId={}, code={}, message={}",
                    context.getTaskId(), warning.getCode(), warning.getMessage());
        }
        return outcome;
    }

    private AssembleReport buildReport(MergeOutcome merged, TreeOutcome tree, ContinuationOutcome continuation) {
        AssembleReport report = new AssembleReport();
        report.setMergePairs(merged.getMergePairs());
        report.setConflictCount(merged.getConflicts().size());
        report.setTitleCount(tree.getTitleCount());
        report.setTitleCountByCascade(ObjectUtil.defaultIfNull(tree.getTitleCountByCascade(), new HashMap<>()));
        report.setTitleCandidateCount(tree.getTitleCandidateCount());
        report.setContinuationCount(continuation.getContinuationCount());
        report.setUnattachableElements(ObjectUtil.defaultIfNull(tree.getUnattachableElements(), new ArrayList<>()));
        return report;
    }

    private void verifyProvenance(List<UnifiedElement> elements, AssembleReport report) {
        int total = elements.size();
        int traceable = 0;
        for (UnifiedElement element : elements) {
            if (ObjectUtil.isNotNull(element.getProvenance())
                    && StrUtil.isNotBlank(element.getProvenance().getFile())
                    && StrUtil.isNotBlank(element.getProvenance().getPath())) {
                traceable++;
            }
        }
        report.setNormalizedCount(total);
        report.setTraceableRatio(total > 0 ? (double) traceable / total : 0);
    }

    private UnifiedDocument buildDocument(ParseResult parseResult, AssembleContext context,
                                          List<UnifiedElement> elements, List<DocumentRelation> relations) {
        UnifiedDocument document = new UnifiedDocument();
        DocumentInfo info = new DocumentInfo();
        info.setDocumentId("doc-" + context.getFileResultId());
        info.setSourceFileRef(parseResult.getFile().getFileId());
        info.setSourceFileType(parseResult.getFile().getMimeType());
        info.setParserRunId(parseResult.getResultId());
        info.setSchemaVersion(UnifiedDocument.SCHEMA_VERSION);
        info.setCapabilitySnapshot(parseResult.getCapabilitySnapshot());
        document.setDocumentInfo(info);
        document.setPages(buildPages(parseResult));
        document.setElements(elements);
        document.setRelations(relations);
        return document;
    }

    private List<UnifiedPage> buildPages(ParseResult parseResult) {
        List<UnifiedPage> pages = new ArrayList<>();
        if (ObjectUtil.isNull(parseResult.getSources())) {
            return pages;
        }
        for (ParseSource source : parseResult.getSources()) {
            if (ObjectUtil.isNull(source.getPageDimensions())) {
                continue;
            }
            for (PageDimension dimension : source.getPageDimensions()) {
                UnifiedPage page = new UnifiedPage();
                page.setPageId("pg-" + dimension.getPage());
                page.setPageNumber(dimension.getPage());
                page.setWidth(dimension.getWidth());
                page.setHeight(dimension.getHeight());
                page.setRotation(0d);
                page.setUnit(PAGE_UNIT);
                page.setOrigin(PAGE_ORIGIN);
                pages.add(page);
            }
            break; // 页面基准取首个含尺寸的路（native）
        }
        return pages;
    }

    private DocumentQuality buildQuality(MergeOutcome merged, TreeOutcome tree,
                                         ContinuationOutcome continuation, AssembleReport report) {
        DocumentQuality quality = new DocumentQuality();
        quality.setConflicts(ObjectUtil.defaultIfNull(merged.getConflicts(), new ArrayList<>()));
        if (tree.getTitleCandidateCount() > 0) {
            quality.getWarnings().add(QualityWarning.of(QualityWarningCode.TITLE_CANDIDATE, null, "WARN",
                    "标题候选 " + tree.getTitleCandidateCount() + " 处，已按正文处理（规则拿不准，可重跑或后续接入模型兜底）"));
        }
        if (continuation.getSuspectedCount() > 0) {
            quality.getWarnings().add(QualityWarning.of(QualityWarningCode.SUSPECTED_CONTINUATION, null, "WARN",
                    "疑似续表 " + continuation.getSuspectedCount() + " 处（放宽规则命中，默认接续 + 表头继承）"));
        }
        if (report.getTraceableRatio() < 1) {
            int missingCount = report.getNormalizedCount()
                    - (int) Math.round(report.getTraceableRatio() * report.getNormalizedCount());
            quality.getWarnings().add(QualityWarning.of(QualityWarningCode.PROVENANCE_MISSING, null, "WARN",
                    String.format("溯源可回溯占比 %.2f，%d 个元素缺原文定位",
                            report.getTraceableRatio(), missingCount)));
        }
        if (report.getNoisePageCount() > 0) {
            quality.getWarnings().add(QualityWarning.of(QualityWarningCode.NOISE_PAGE, null, "WARN",
                    "噪声页 " + report.getNoisePageCount() + " 页已标记（空白/纯图片/乱码；处置在预处理环节）"));
        }
        return quality;
    }

    private List<DocumentRelation> mergeRelations(List<DocumentRelation> treeRelations,
                                                  List<DocumentRelation> continuationRelations) {
        List<DocumentRelation> all = new ArrayList<>(ObjectUtil.defaultIfNull(treeRelations, new ArrayList<>()));
        all.addAll(ObjectUtil.defaultIfNull(continuationRelations, new ArrayList<>()));
        return all;
    }
}
