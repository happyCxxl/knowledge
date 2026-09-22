package com.knowledge.worker.parser.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.common.domain.parse.CapabilitySnapshot;
import com.knowledge.common.domain.parse.ParseOutcome;
import com.knowledge.common.domain.parse.ParseResult;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.domain.parse.QualityInfo;
import com.knowledge.common.domain.task.StepLogInfo;
import com.knowledge.common.domain.parse.signal.Signal;
import com.knowledge.common.enums.parse.ParseStepName;
import com.knowledge.common.enums.parse.SignalSubtype;
import com.knowledge.common.enums.task.PipelineTaskErrorCode;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.enums.task.StepStatus;
import com.knowledge.worker.parser.DocumentParserPort;
import com.knowledge.worker.parser.ParseContext;
import com.knowledge.worker.parser.ParseProperties;
import com.knowledge.worker.parser.signal.SignalDetector;
import com.knowledge.worker.parser.signal.SignalFallbackHandler;
import com.knowledge.worker.pipeline.StepLogHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 解析环节编排（模板方法骨架）：文件级路由 → 原生解析 → 信号判定 →
 * 内置降级处置（策略：SignalFallbackHandler）→ 结果封装 → 成功占比门槛评估。
 * 骨架固定，变化点在策略（DocumentParserPort 解析器 / SignalFallbackHandler 信号处置）；
 * 纯算法，不碰 DB/产物存储（落库回写由 biz 编排）。
 *
 * @author cxxl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParsePipeline {

    private final List<DocumentParserPort> parsers;
    private final SignalDetector signalDetector;
    private final List<SignalFallbackHandler> fallbackHandlers;
    private final ParseProperties properties;

    public ParseOutcome run(ParseContext context) {
        ParseOutcome outcome = new ParseOutcome();
        ParseResult result = new ParseResult();
        result.setResultId(context.getFileResultId());
        result.setFile(context.getFileRef());
        QualityInfo quality = new QualityInfo();
        result.setQuality(quality);

        // ① 文件级路由
        StepLogInfo routeLog = StepLogHelper.begin(ParseStepName.FILE_ROUTE.value());
        String mimeType = context.getFileRef().getMimeType();
        DocumentParserPort parser = parsers.stream()
                .filter(p -> p.supports(mimeType))
                .findFirst()
                .orElse(null);
        if (ObjectUtil.isNull(parser)) {
            log.warn("===> ParsePipeline 无匹配解析器, fileResultId={}, mimeType={}",
                    context.getFileResultId(), mimeType);
            routeLog.setStatus(StepStatus.FAILED.name());
            routeLog.setError("无匹配解析器: " + mimeType);
            StepLogHelper.finish(routeLog);
            outcome.getStepLogs().add(routeLog);
            outcome.fail(PipelineTaskErrorCode.PARSE_FAILED.name(), "无匹配解析器: " + mimeType);
            return outcome;
        }
        routeLog.setCapabilityVersion(parser.capabilityName() + "-" + parser.capabilityVersion());
        routeLog.setStatus(StepStatus.SUCCESS.name());
        StepLogHelper.finish(routeLog);
        outcome.getStepLogs().add(routeLog);
        log.info("===> ParsePipeline 路由命中, fileResultId={}, mimeType={}, parser={}",
                context.getFileResultId(), mimeType, parser.capabilityName() + "-" + parser.capabilityVersion());

        // ② 原生解析
        StepLogInfo parseLog = StepLogHelper.begin(ParseStepName.NATIVE_PARSE.value());
        parseLog.setCapabilityVersion(parser.capabilityName() + "-" + parser.capabilityVersion());
        ParseSource nativeSource;
        try {
            // 解析
            nativeSource = parser.parse(context);
            parseLog.setStatus(StepStatus.SUCCESS.name());
        } catch (Exception e) {
            log.warn("===> ParsePipeline 原生解析失败, fileResultId={}, error={}",
                    context.getFileResultId(), StrUtil.maxLength(String.valueOf(e.getMessage()), 500));
            parseLog.setStatus(StepStatus.FAILED.name());
            parseLog.setError(StrUtil.maxLength(String.valueOf(e.getMessage()), 1000));
            StepLogHelper.finish(parseLog);
            outcome.getStepLogs().add(parseLog);
            outcome.fail(PipelineTaskErrorCode.PARSE_CORRUPTED.name(),
                    "解析失败: " + StrUtil.maxLength(String.valueOf(e.getMessage()), 500));
            return outcome;
        }
        StepLogHelper.finish(parseLog);
        outcome.getStepLogs().add(parseLog);
        log.info("===> ParsePipeline 原生解析完成, fileResultId={}, parser={}, elements={}, unitCount={}",
                context.getFileResultId(), parser.capabilityName() + "-" + parser.capabilityVersion(),
                nativeSource.getElements().size(), nativeSource.getUnitCount());

        // ③ 信号判定 + 内置降级处置
        // 扩展点（预留）：接入 OCR/版面/表格能力后，在此按信号类型查 CapabilityRegistry，
        // 把能力结果并入 result.sources 新增一路；一期无能力实现，全部信号走内置降级。
        StepLogInfo qualityLog = StepLogHelper.begin(ParseStepName.QUALITY_CHECK.value());
        List<Signal> signals = signalDetector.detect(nativeSource, context);
        int failedUnits = 0;
        for (Signal signal : signals) {
            failedUnits += applyFallback(signal, quality);
        }
        log.info("===> ParsePipeline 信号判定完成, fileResultId={}, signalCount={}, failedUnits={}",
                context.getFileResultId(), signals.size(), failedUnits);

        // ④ 结果封装：能力快照 + ocr 占位路
        CapabilitySnapshot snapshot = new CapabilitySnapshot();
        snapshot.setParserName(parser.capabilityName());
        snapshot.setParserVersion(parser.capabilityVersion());
        result.setCapabilitySnapshot(snapshot);
        ParseSource ocrPlaceholder = new ParseSource();
        ocrPlaceholder.setSource("ocr");
        ocrPlaceholder.setCandidateOrder(false);
        ocrPlaceholder.setNote("预留，一期不接：接入后扫描/图片区域文字走本路，与 native 路重叠区域由组装环节裁决");
        result.setSources(List.of(nativeSource, ocrPlaceholder));

        // ⑤ 成功占比门槛评估
        evaluate(outcome, nativeSource, failedUnits, quality);

        // 失败诊断留痕：告警明细进质量检查子步骤（落 kb_pipeline_step_log.error，可查询）
        if (PipelineTaskStatus.FAILED.name().equals(outcome.getSuggestedStatus())) {
            String summary = quality.getWarnings().stream()
                    .map(w -> w.getCode() + ":" + w.getMessage())
                    .collect(Collectors.joining("; "));
            qualityLog.setError(StrUtil.maxLength(summary, 1000));
        }

        qualityLog.setWarningCount(quality.getWarnings().size());
        qualityLog.setStatus(StepStatus.SUCCESS.name());
        StepLogHelper.finish(qualityLog);
        outcome.getStepLogs().add(qualityLog);
        outcome.setParseResult(result);
        return outcome;
    }

    /** 内置降级处置：按信号子类型查找 handler 执行（不依赖 evidence 文案）；返回该信号计入的失败单元数 */
    private int applyFallback(Signal signal, QualityInfo quality) {
        SignalSubtype subtype = parseSubtype(signal);
        if (ObjectUtil.isNull(subtype)) {
            return 0;
        }
        return fallbackHandlers.stream()
                .filter(handler -> handler.subtype() == subtype)
                .findFirst()
                .map(handler -> handler.handle(signal, quality))
                .orElse(0);
    }

    /** 信号子类型解析：未设置/未知值返回 null（防御，不判失败）。 */
    private SignalSubtype parseSubtype(Signal signal) {
        if (StrUtil.isBlank(signal.getSubtype())) {
            return null;
        }
        try {
            return SignalSubtype.valueOf(signal.getSubtype());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** 成功占比门槛评估：0 单元→PARSE_CORRUPTED；全扫描页→SCANNED_UNSUPPORTED；占比不足→RATIO_BELOW_THRESHOLD。 */
    private void evaluate(ParseOutcome outcome, ParseSource nativeSource, int failedUnits, QualityInfo quality) {
        int unitCount = ObjectUtil.isNull(nativeSource.getUnitCount()) ? 0 : nativeSource.getUnitCount();
        outcome.setUnitCount(unitCount);
        outcome.setFailedUnits(failedUnits);
        if (unitCount <= 0) {
            outcome.fail(PipelineTaskErrorCode.PARSE_CORRUPTED.name(), "无有效内容单元（0 页/0 sheet）");
            return;
        }
        // 纯扫描件：全部单元判为扫描页 → 整任务 FAILED
        if (!quality.getScannedPages().isEmpty() && quality.getScannedPages().size() == unitCount) {
            outcome.fail(PipelineTaskErrorCode.SCANNED_UNSUPPORTED.name(), "扫描件暂不支持（OCR 预留）");
            return;
        }
        double ratio = (double) (unitCount - failedUnits) / unitCount;
        if (ratio >= properties.getSuccessUnitRatio()) {
            outcome.setSuggestedStatus(failedUnits == 0
                    ? PipelineTaskStatus.SUCCESS.name()
                    : PipelineTaskStatus.PARTIAL_SUCCESS.name());
            return;
        }
        // 门槛不足：打印每页原始指标，便于定位信号误伤（扫描件/乱码口径/文字占比口径）
        log.warn("解析成功占比不足, unitCount={}, failedUnits={}, ratio={}, threshold={}, pageMetrics={}",
                unitCount, failedUnits, String.format("%.2f", ratio), properties.getSuccessUnitRatio(),
                nativeSource.getPageMetrics().stream()
                        .map(m -> m.getPage() + ":chars=" + m.getCharCount()
                                + ",garbled=" + String.format("%.2f", m.getGarbledRatio())
                                + ",area=" + String.format("%.2f", m.getTextAreaRatio()))
                        .toList());
        outcome.fail(PipelineTaskErrorCode.RATIO_BELOW_THRESHOLD.name(),
                String.format("成功单元占比 %.2f 低于门槛 %.2f", ratio, properties.getSuccessUnitRatio()));
    }
}
