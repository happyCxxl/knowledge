package com.knowledge.worker.structure.impl.craft;
import com.knowledge.common.enums.structure.UnifiedElementType;

import com.knowledge.common.domain.structure.DocumentRelation;
import com.knowledge.common.domain.structure.UnifiedElement;
import com.knowledge.common.domain.parse.FontInfo;
import com.knowledge.worker.structure.AssembleContext;
import com.knowledge.worker.structure.StructureProperties;
import com.knowledge.worker.structure.craft.TreeOutcome;
import com.knowledge.worker.structure.impl.StructureJudgeRegistry;
import com.knowledge.worker.structure.impl.title.ChapterTitleRule;
import com.knowledge.worker.structure.impl.title.CnDotTitleRule;
import com.knowledge.worker.structure.impl.title.CnParenTitleRule;
import com.knowledge.worker.structure.impl.title.FontSignalTitleRule;
import com.knowledge.worker.structure.impl.title.NumberTitleRule;
import com.knowledge.worker.structure.impl.title.StyleTitleRule;
import com.knowledge.worker.structure.title.TitleRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构组装单测：标题判定规则链（六规则真实实例，样式/编号模式/字号加粗/候选告警）+ Excel sheet→SECTION + 章节树。
 *
 * @author cxxl
 */
class StructureAssemblerImplTest {

    private StructureAssemblerImpl assembler;
    private StructureProperties properties;

    @BeforeEach
    void setUp() {
        properties = new StructureProperties();
        List<TitleRule> rules = List.of(new StyleTitleRule(), new ChapterTitleRule(),
                new NumberTitleRule(), new CnParenTitleRule(), new CnDotTitleRule(),
                new FontSignalTitleRule());
        assembler = new StructureAssemblerImpl(
                new StructureJudgeRegistry(new ArrayList<>(), properties), rules);
    }

    private AssembleContext context(String mimeType) {
        AssembleContext context = new AssembleContext();
        context.setFileResultId(1L);
        context.setSourceFileType(mimeType);
        context.setProperties(properties);
        return context;
    }

    private UnifiedElement paragraph(String text, Double size, Boolean bold) {
        UnifiedElement element = new UnifiedElement();
        element.setId("n-" + Math.abs(text.hashCode()));
        element.setType(UnifiedElementType.PARAGRAPH.name());
        element.setText(text);
        element.setFont(new FontInfo("SimSun", size, bold));
        return element;
    }

    @Test
    void styleTitleShouldGetLevelFromHeading() {
        UnifiedElement styled = paragraph("第一章 投标人须知", 12d, false);
        styled.setExtension(Map.of("style", "Heading1"));

        assembler.assembleTree(List.of(styled), context("application/pdf"));

        assertEquals(UnifiedElementType.TITLE.name(), styled.getType());
        assertEquals(1, styled.getLevel());
        assertEquals("style", styled.getTitleEvidence().getCascade());
    }

    @Test
    void chapterPatternShouldBeLevelOneTitle() {
        UnifiedElement chapter = paragraph("第三章 技术要求", 14d, true);

        assembler.assembleTree(List.of(chapter), context("application/pdf"));

        assertEquals(UnifiedElementType.TITLE.name(), chapter.getType());
        assertEquals(1, chapter.getLevel());
        assertEquals("number-pattern", chapter.getTitleEvidence().getCascade());
        assertEquals("第X章", chapter.getTitleEvidence().getPattern());
    }

    @Test
    void deepNumberPatternShouldGetLevelByDots() {
        UnifiedElement deep = paragraph("1.2.3.4.1 某某条款", 12d, true);

        assembler.assembleTree(List.of(deep), context("application/pdf"));

        assertEquals(UnifiedElementType.TITLE.name(), deep.getType());
        assertEquals(5, deep.getLevel());
        assertEquals("number-pattern", deep.getTitleEvidence().getCascade());
    }

    @Test
    void numberPatternWithoutFontSignalShouldBeCandidate() {
        UnifiedElement suspicious = paragraph("3.14 是 π", 12d, false);

        TreeOutcome outcome = assembler.assembleTree(List.of(suspicious), context("application/pdf"));

        assertEquals(UnifiedElementType.PARAGRAPH.name(), suspicious.getType());
        assertEquals(1, outcome.getTitleCandidateCount());
        assertTrue(outcome.getTitleCountByCascade().isEmpty());
    }

    @Test
    void boldLargeFontShouldBeTitleByFontSignal() {
        UnifiedElement boldLarge = paragraph("总体要求", 16d, true);
        UnifiedElement body = paragraph("本须知适用于参与本次采购活动的所有供应商。", 10d, false);
        UnifiedElement body2 = paragraph("投标保证金为人民币叁佰万元整。", 10d, false);

        TreeOutcome outcome = assembler.assembleTree(List.of(boldLarge, body, body2), context("application/pdf"));

        assertEquals(UnifiedElementType.TITLE.name(), boldLarge.getType());
        assertEquals("font-signal", boldLarge.getTitleEvidence().getCascade());
        assertEquals(1, outcome.getTitleCount());
    }

    @Test
    void chapterTreeShouldAttachContentToNearestTitle() {
        UnifiedElement title = paragraph("第一章 总则", 14d, true);
        UnifiedElement content = paragraph("投标保证金为人民币叁佰万元整。", 10d, false);

        TreeOutcome outcome = assembler.assembleTree(List.of(title, content), context("application/pdf"));

        List<DocumentRelation> parentChild = outcome.getRelations().stream()
                .filter(r -> "PARENT_CHILD".equals(r.getType()))
                .toList();
        assertEquals(1, parentChild.size());
        assertEquals(title.getId(), parentChild.getFirst().getFrom());
        assertEquals(content.getId(), parentChild.getFirst().getTo());
        // 阅读顺序关系
        assertTrue(outcome.getRelations().stream().anyMatch(r -> "NEXT".equals(r.getType())));
    }

    @Test
    void excelShouldBuildSheetSections() {
        UnifiedElement table = new UnifiedElement();
        table.setId("t-1");
        table.setType(UnifiedElementType.TABLE.name());
        table.setText("评分表");
        table.setExtension(Map.of("sheetName", "评分表"));

        TreeOutcome outcome = assembler.assembleTree(new ArrayList<>(List.of(table)),
                context("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));

        assertEquals(2, outcome.getElements().size());
        UnifiedElement section = outcome.getElements().getFirst();
        assertEquals(UnifiedElementType.SECTION.name(), section.getType());
        assertEquals("评分表", section.getText());
        assertTrue(outcome.getRelations().stream().anyMatch(r ->
                "PARENT_CHILD".equals(r.getType()) && section.getId().equals(r.getFrom())
                        && "t-1".equals(r.getTo())));
    }
}
