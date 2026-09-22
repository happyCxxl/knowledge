package com.knowledge.worker.parser.impl;

import com.knowledge.common.domain.input.FileReference;
import com.knowledge.common.domain.parse.ParseOutcome;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.domain.parse.signal.PageMetric;
import com.knowledge.common.domain.parse.signal.ParseFact;
import com.knowledge.common.enums.task.PipelineTaskErrorCode;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.enums.parse.SignalType;
import com.knowledge.worker.parser.DocumentParserPort;
import com.knowledge.worker.parser.ParseContext;
import com.knowledge.worker.parser.ParseProperties;
import com.knowledge.worker.parser.signal.SignalFallbackHandler;
import com.knowledge.worker.parser.impl.fallback.GarbledFallbackHandler;
import com.knowledge.worker.parser.impl.fallback.ImageEmbeddedFallbackHandler;
import com.knowledge.worker.parser.impl.fallback.ImageLowRatioFallbackHandler;
import com.knowledge.worker.parser.impl.fallback.LayoutRuleFailedFallbackHandler;
import com.knowledge.worker.parser.impl.fallback.ScannedFallbackHandler;
import com.knowledge.worker.parser.impl.fallback.TableRuleFailedFallbackHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 解析管线编排单测：路由、门槛三档、纯扫描件、信号降级处置（六 handler 真实实例）。
 *
 * @author cxxl
 */
@ExtendWith(MockitoExtension.class)
class ParsePipelineTest {

    private ParseProperties properties;
    private SignalDetectorImpl signalDetector;

    @BeforeEach
    void setUp() {
        properties = new ParseProperties();
        signalDetector = new SignalDetectorImpl();
    }

    /** 六个信号降级 handler 真实实例（无 mock） */
    private List<SignalFallbackHandler> fallbackHandlers() {
        return List.of(new GarbledFallbackHandler(), new ScannedFallbackHandler(),
                new ImageEmbeddedFallbackHandler(), new ImageLowRatioFallbackHandler(),
                new TableRuleFailedFallbackHandler(), new LayoutRuleFailedFallbackHandler());
    }

    private ParseContext context(String mimeType) {
        ParseContext context = new ParseContext();
        context.setFileResultId(1L);
        FileReference fileRef = new FileReference();
        fileRef.setFileId("F-1");
        fileRef.setFileName("t");
        fileRef.setMimeType(mimeType);
        context.setFileRef(fileRef);
        context.setInputStream(new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)));
        context.setProperties(properties);
        return context;
    }

    private DocumentParserPort fakeParser(List<PageMetric> metrics, List<ParseFact> facts) {
        return new DocumentParserPort() {
            @Override
            public boolean supports(String mime) {
                return "application/pdf".equals(mime);
            }

            @Override
            public ParseSource parse(ParseContext context) {
                ParseSource source = ParseSource.nativeSource("fake-1.0");
                source.setUnitCount(metrics.size());
                source.setPageMetrics(metrics);
                source.setFacts(facts);
                return source;
            }

            @Override
            public String capabilityName() {
                return "fake";
            }

            @Override
            public String capabilityVersion() {
                return "1.0";
            }
        };
    }

    private ParsePipeline pipeline(DocumentParserPort parser) {
        return new ParsePipeline(List.of(parser), signalDetector, fallbackHandlers(), properties);
    }

    private PageMetric normalPage(int page) {
        PageMetric metric = new PageMetric();
        metric.setPage(page);
        metric.setCharCount(500);
        metric.setGarbledRatio(0.01);
        metric.setTextAreaRatio(0.3);
        return metric;
    }

    private PageMetric scannedPage(int page) {
        PageMetric metric = new PageMetric();
        metric.setPage(page);
        metric.setCharCount(10);
        metric.setGarbledRatio(0);
        metric.setTextAreaRatio(0.01);
        return metric;
    }

    private PageMetric garbledPage() {
        PageMetric metric = new PageMetric();
        metric.setPage(1);
        metric.setCharCount(500);
        metric.setGarbledRatio(0.9);
        metric.setTextAreaRatio(0.3);
        return metric;
    }

    @Test
    void allPagesOkShouldSuggestSuccess() {
        ParsePipeline pipeline = pipeline(fakeParser(
                List.of(normalPage(1), normalPage(2)), List.of()));

        ParseOutcome outcome = pipeline.run(context("application/pdf"));

        assertEquals(PipelineTaskStatus.SUCCESS.name(), outcome.getSuggestedStatus());
        assertEquals(0, outcome.getFailedUnits());
        assertNotNull(outcome.getParseResult().getCapabilitySnapshot());
        assertEquals(3, outcome.getStepLogs().size());
        assertEquals(2, outcome.getParseResult().getSources().size());
        assertEquals("ocr", outcome.getParseResult().getSources().get(1).getSource());
    }

    @Test
    void oneScannedPageOutOfTenShouldSuggestPartialSuccess() {
        List<PageMetric> metrics = new java.util.ArrayList<>();
        metrics.add(scannedPage(1));
        for (int i = 2; i <= 10; i++) {
            metrics.add(normalPage(i));
        }
        ParsePipeline pipeline = pipeline(fakeParser(metrics, List.of()));

        ParseOutcome outcome = pipeline.run(context("application/pdf"));

        assertEquals(PipelineTaskStatus.PARTIAL_SUCCESS.name(), outcome.getSuggestedStatus());
        assertEquals(1, outcome.getFailedUnits());
        assertEquals(1, outcome.getParseResult().getQuality().getScannedPages().size());
        assertEquals(1, outcome.getParseResult().getQuality().getWarnings().size());
    }

    @Test
    void allScannedShouldFailScannedUnsupported() {
        ParsePipeline pipeline = pipeline(fakeParser(
                List.of(scannedPage(1), scannedPage(2), scannedPage(3)), List.of()));

        ParseOutcome outcome = pipeline.run(context("application/pdf"));

        assertEquals(PipelineTaskStatus.FAILED.name(), outcome.getSuggestedStatus());
        assertEquals(PipelineTaskErrorCode.SCANNED_UNSUPPORTED.name(), outcome.getErrorCode());
    }

    @Test
    void ratioBelowThresholdShouldFail() {
        List<PageMetric> metrics = new java.util.ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            metrics.add(scannedPage(i));
        }
        for (int i = 6; i <= 10; i++) {
            metrics.add(normalPage(i));
        }
        ParsePipeline pipeline = pipeline(fakeParser(metrics, List.of()));

        ParseOutcome outcome = pipeline.run(context("application/pdf"));

        assertEquals(PipelineTaskStatus.FAILED.name(), outcome.getSuggestedStatus());
        assertEquals(PipelineTaskErrorCode.RATIO_BELOW_THRESHOLD.name(), outcome.getErrorCode());
    }

    @Test
    void tableFactShouldWarnWithoutFailing() {
        ParseFact fact = new ParseFact();
        fact.setType(SignalType.TABLE.name());
        fact.setRegion("page 1");
        fact.setEvidence("列对齐聚类失败（疑似无边框/复杂表格）");
        ParsePipeline pipeline = pipeline(fakeParser(List.of(normalPage(1)), List.of(fact)));

        ParseOutcome outcome = pipeline.run(context("application/pdf"));

        assertEquals(PipelineTaskStatus.SUCCESS.name(), outcome.getSuggestedStatus());
        assertEquals(0, outcome.getFailedUnits());
        assertEquals(1, outcome.getParseResult().getQuality().getWarnings().size());
        assertEquals("TABLE_RULE_FALLBACK", outcome.getParseResult().getQuality().getWarnings().getFirst().getCode());
    }

    @Test
    void layoutFactShouldWarnWithoutFailing() {
        ParseFact fact = new ParseFact();
        fact.setType(SignalType.LAYOUT.name());
        fact.setRegion("page 1");
        fact.setEvidence("版面规则失败");
        ParsePipeline pipeline = pipeline(fakeParser(List.of(normalPage(1)), List.of(fact)));

        ParseOutcome outcome = pipeline.run(context("application/pdf"));

        assertEquals(PipelineTaskStatus.SUCCESS.name(), outcome.getSuggestedStatus());
        assertEquals(0, outcome.getFailedUnits());
        assertEquals("LAYOUT_RULE_FALLBACK", outcome.getParseResult().getQuality().getWarnings().getFirst().getCode());
    }

    @Test
    void embeddedImageFactShouldWarnWithoutFailing() {
        ParseFact fact = new ParseFact();
        fact.setType(SignalType.OCR_IMAGE.name());
        fact.setRegion("图 1");
        fact.setEvidence("-");
        ParsePipeline pipeline = pipeline(fakeParser(List.of(normalPage(1)), List.of(fact)));

        ParseOutcome outcome = pipeline.run(context("application/pdf"));

        assertEquals(PipelineTaskStatus.SUCCESS.name(), outcome.getSuggestedStatus());
        assertEquals(0, outcome.getFailedUnits());
        assertEquals("IMAGE_TEXT_UNRECOGNIZED", outcome.getParseResult().getQuality().getWarnings().getFirst().getCode());
    }

    @Test
    void garbledPageShouldCountFailedUnit() {
        List<PageMetric> metrics = new java.util.ArrayList<>();
        metrics.add(garbledPage());
        for (int i = 2; i <= 10; i++) {
            metrics.add(normalPage(i));
        }
        ParsePipeline pipeline = pipeline(fakeParser(metrics, List.of()));

        ParseOutcome outcome = pipeline.run(context("application/pdf"));

        assertEquals(PipelineTaskStatus.PARTIAL_SUCCESS.name(), outcome.getSuggestedStatus());
        assertEquals(1, outcome.getFailedUnits());
        assertEquals(1, outcome.getParseResult().getQuality().getFailedPages().size());
        assertEquals("GARBLED_PAGE", outcome.getParseResult().getQuality().getWarnings().getFirst().getCode());
    }

    @Test
    void suspectedImagePageShouldWarnWithoutFailing() {
        // 有文字（chars 充足）但文字占比低：疑似图片页 → 仅告警，不计失败单元
        PageMetric sparse = new PageMetric();
        sparse.setPage(1);
        sparse.setCharCount(300);
        sparse.setGarbledRatio(0.01);
        sparse.setTextAreaRatio(0.02);
        ParsePipeline pipeline = pipeline(fakeParser(List.of(sparse), List.of()));

        ParseOutcome outcome = pipeline.run(context("application/pdf"));

        assertEquals(PipelineTaskStatus.SUCCESS.name(), outcome.getSuggestedStatus());
        assertEquals(0, outcome.getFailedUnits());
        assertEquals(1, outcome.getParseResult().getQuality().getWarnings().size());
        assertEquals("IMAGE_PAGE_SUSPECTED", outcome.getParseResult().getQuality().getWarnings().getFirst().getCode());
    }

    @Test
    void noParserShouldFail() {
        ParsePipeline pipeline = pipeline(fakeParser(List.of(), List.of()));

        ParseOutcome outcome = pipeline.run(context("application/msword"));

        assertEquals(PipelineTaskStatus.FAILED.name(), outcome.getSuggestedStatus());
        assertEquals(PipelineTaskErrorCode.PARSE_FAILED.name(), outcome.getErrorCode());
    }
}
