package com.knowledge.worker.structure.impl;

import com.knowledge.common.domain.input.FileReference;
import com.knowledge.common.domain.parse.ParseElement;
import com.knowledge.common.domain.parse.ParseResult;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.domain.parse.Provenance;
import com.knowledge.common.domain.parse.QualityWarning;
import com.knowledge.common.domain.structure.AssembleOutcome;
import com.knowledge.common.domain.structure.UnifiedElement;
import com.knowledge.common.enums.parse.ElementType;
import com.knowledge.common.enums.parse.QualityWarningCode;
import com.knowledge.common.enums.task.PipelineTaskErrorCode;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.worker.structure.AssembleContext;
import com.knowledge.worker.structure.StructureProperties;
import com.knowledge.worker.structure.impl.craft.DedupMergerImpl;
import com.knowledge.worker.structure.impl.craft.NormalizerImpl;
import com.knowledge.worker.structure.impl.craft.ReadingOrderResolverImpl;
import com.knowledge.worker.structure.impl.craft.RepeatNoiseMarkerImpl;
import com.knowledge.worker.structure.impl.craft.StructureAssemblerImpl;
import com.knowledge.worker.structure.impl.craft.TableContinuationResolverImpl;
import com.knowledge.worker.structure.impl.title.ChapterTitleRule;
import com.knowledge.worker.structure.impl.title.CnDotTitleRule;
import com.knowledge.worker.structure.impl.title.CnParenTitleRule;
import com.knowledge.worker.structure.impl.title.FontSignalTitleRule;
import com.knowledge.worker.structure.impl.title.NumberTitleRule;
import com.knowledge.worker.structure.impl.title.SingleNumberTitleRule;
import com.knowledge.worker.structure.impl.title.StyleTitleRule;
import com.knowledge.worker.structure.title.TitleRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 组装管线单测：未识别元素类型的跳过计数与质量告警（避免跨环节类型集合不匹配时静默丢元素）。
 *
 * @author cxxl
 */
class AssemblerPipelineTest {

    /** 两侧枚举都不存在的类型名（模拟解析侧新增类型） */
    private static final String UNKNOWN_TYPE = "CUSTOM_BLOCK";

    /** Excel MIME（触发 sheet → SECTION 分支） */
    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private AssemblerPipeline pipeline;
    private StructureProperties properties;

    @BeforeEach
    void setUp() {
        properties = new StructureProperties();
        StructureJudgeRegistry registry = new StructureJudgeRegistry(new ArrayList<>(), properties);
        List<TitleRule> rules = List.of(new StyleTitleRule(), new ChapterTitleRule(),
                new SingleNumberTitleRule(), new NumberTitleRule(), new CnParenTitleRule(),
                new CnDotTitleRule(), new FontSignalTitleRule());
        pipeline = new AssemblerPipeline(new NormalizerImpl(), new DedupMergerImpl(),
                new ReadingOrderResolverImpl(), new StructureAssemblerImpl(registry, rules),
                new TableContinuationResolverImpl(registry), new RepeatNoiseMarkerImpl(properties));
    }

    private AssembleContext context() {
        AssembleContext context = new AssembleContext();
        context.setTaskId(1L);
        context.setFileResultId(2L);
        context.setProperties(properties);
        return context;
    }

    private ParseResult parseResult(ParseElement... elements) {
        return parseResult("application/pdf", elements);
    }

    private ParseResult parseResult(String mimeType, ParseElement... elements) {
        ParseResult result = new ParseResult();
        result.setResultId(2L);
        FileReference file = new FileReference();
        file.setFileId("f-1");
        file.setMimeType(mimeType);
        result.setFile(file);
        ParseSource source = ParseSource.nativeSource("pdfbox-3.0.4");
        source.setElements(new ArrayList<>(List.of(elements)));
        result.setSources(List.of(source));
        return result;
    }

    /** Excel 表格：sheetName 生成 SECTION，单元格按需带溯源 */
    private ParseElement sheetTable(String id, String sheetName, boolean withCellProvenance) {
        ParseElement table = ParseElement.of(id, ElementType.TABLE);
        table.setSheetName(sheetName);
        table.setPage(1);
        table.setRows(1);
        table.setCols(1);
        ParseElement cell = ParseElement.of(id + "-c0", ElementType.TABLE_CELL);
        cell.setRow(0);
        cell.setCol(0);
        cell.setText("甲");
        if (withCellProvenance) {
            cell.setProvenance(new Provenance("f-1", "office#sheet[" + sheetName + "]/cell[0,0]"));
        }
        table.setCells(List.of(cell));
        return table;
    }

    private ParseElement paragraph(String id, String text) {
        ParseElement element = ParseElement.of(id, ElementType.PARAGRAPH);
        element.setText(text);
        return element;
    }

    private ParseElement custom(String id, String type, String text) {
        ParseElement element = ParseElement.of(id, ElementType.PARAGRAPH);
        element.setType(type);
        element.setText(text);
        return element;
    }

    private List<String> warningMessages(AssembleOutcome outcome, QualityWarningCode code) {
        return outcome.getDocument().getQuality().getWarnings().stream()
                .filter(w -> code.name().equals(w.getCode()))
                .map(QualityWarning::getMessage)
                .toList();
    }

    @Test
    void unmappedTypeShouldSkipElementAndWarn() {
        AssembleOutcome outcome = pipeline.assemble(
                parseResult(paragraph("p1", "投标保证金为人民币叁佰万元整。"), custom("u1", UNKNOWN_TYPE, "图注内容")),
                context());

        assertEquals(PipelineTaskStatus.SUCCESS.name(), outcome.getSuggestedStatus());
        List<UnifiedElement> elements = outcome.getDocument().getElements();
        assertEquals(1, elements.size());
        assertEquals("投标保证金为人民币叁佰万元整。", elements.getFirst().getText());

        List<String> messages = warningMessages(outcome, QualityWarningCode.ELEMENT_TYPE_UNMAPPED);
        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().contains(UNKNOWN_TYPE), messages.getFirst());
        assertTrue(messages.getFirst().contains("共 1 个元素已跳过"), messages.getFirst());
    }

    @Test
    void blankTypeShouldBeCountedAsBlank() {
        AssembleOutcome outcome = pipeline.assemble(
                parseResult(paragraph("p1", "正文内容"), custom("u1", null, "无类型元素")), context());

        List<String> messages = warningMessages(outcome, QualityWarningCode.ELEMENT_TYPE_UNMAPPED);
        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().contains("(blank)"), messages.getFirst());
    }

    @Test
    void allUnmappedShouldFailWithTypeHint() {
        AssembleOutcome outcome = pipeline.assemble(
                parseResult(custom("u1", UNKNOWN_TYPE, "图注内容")), context());

        assertEquals(PipelineTaskStatus.FAILED.name(), outcome.getSuggestedStatus());
        assertEquals(PipelineTaskErrorCode.STRUCTURE_EMPTY.name(), outcome.getErrorCode());
        assertTrue(outcome.getErrorMsg().contains("类型未识别"), outcome.getErrorMsg());
    }

    @Test
    void knownTypesShouldNotWarnUnmapped() {
        AssembleOutcome outcome = pipeline.assemble(
                parseResult(paragraph("p1", "正文内容一"), paragraph("p2", "正文内容二")), context());

        assertEquals(PipelineTaskStatus.SUCCESS.name(), outcome.getSuggestedStatus());
        assertEquals(0, warningMessages(outcome, QualityWarningCode.ELEMENT_TYPE_UNMAPPED).size());
    }

    @Test
    void excelSheetTableShouldNotWarnProvenance() {
        // 章节节点不计入分母；表格经由单元格定位回原文 → 覆盖率 100%，不再恒定告警
        AssembleOutcome outcome = pipeline.assemble(
                parseResult(XLSX_MIME, sheetTable("t-1", "评分表", true)), context());

        assertEquals(PipelineTaskStatus.SUCCESS.name(), outcome.getSuggestedStatus());
        assertEquals(0, warningMessages(outcome, QualityWarningCode.PROVENANCE_MISSING).size());
    }

    @Test
    void tableWithoutAnyProvenanceShouldStillWarnProvenance() {
        AssembleOutcome outcome = pipeline.assemble(
                parseResult(XLSX_MIME, sheetTable("t-1", "评分表", false)), context());

        List<String> messages = warningMessages(outcome, QualityWarningCode.PROVENANCE_MISSING);
        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().contains("1 个元素缺原文定位"), messages.getFirst());
    }
}
