package com.knowledge.worker.preprocessing.impl.rule;
import com.knowledge.common.enums.preprocess.ViewElementStatus;

import com.knowledge.common.domain.preprocess.TraceEntry;
import com.knowledge.common.domain.preprocess.ViewCell;
import com.knowledge.common.domain.preprocess.ViewElement;
import com.knowledge.worker.preprocessing.PreprocessProperties;
import com.knowledge.worker.preprocessing.strategy.PreprocessStrategy;
import com.knowledge.worker.preprocessing.rule.RuleContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 段落/表格文本整理规则单测：连字符/软连字符/重复标点/项目符号；表格无变化记 KEEP。
 *
 * @author cxxl
 */
class TextTidyRuleTest {

    private final TextTidyRule rule = new TextTidyRule();

    private RuleContext context() {
        RuleContext context = new RuleContext();
        context.setStrategy(PreprocessStrategy.defaultStrategy());
        context.setProperties(new PreprocessProperties());
        return context;
    }

    private ViewElement paragraph(String display) {
        ViewElement element = new ViewElement();
        element.setElementId("n-1");
        element.setType("PARAGRAPH");
        element.setStatus(ViewElementStatus.NORMAL.name());
        element.setRawText(display);
        element.setDisplayText(display);
        element.setNormalizedText(display);
        return element;
    }

    @Test
    void hyphenationAndSoftHyphenShouldJoin() {
        ViewElement element = paragraph("well- known tech\u00ADnology");

        var outcome = rule.apply(element, context());

        assertTrue(outcome.isMatched());
        assertEquals("well-known technology", element.getDisplayText());
    }

    @Test
    void repeatedPunctuationAndBulletShouldTidy() {
        ViewElement element = paragraph("好！！•要点");

        var outcome = rule.apply(element, context());

        assertTrue(outcome.isMatched());
        assertEquals("好！• 要点", element.getDisplayText());
    }

    @Test
    void unchangedTableCellsShouldKeepTrace() {
        ViewElement element = paragraph(null);
        element.setType("TABLE");
        ViewCell cell = new ViewCell();
        cell.setCellId("tc-1");
        cell.setText("评分项");
        cell.setNormalizedText("评分项");
        element.setCells(List.of(cell));

        var outcome = rule.apply(element, context());

        assertTrue(outcome.isMatched());
        assertTrue(outcome.getTraces().stream()
                .anyMatch(t -> TraceEntry.ACTION_KEEP.equals(t.getAction())));
    }

    @Test
    void unchangedParagraphShouldNotMatch() {
        assertFalse(rule.apply(paragraph("正文段落无任何需要整理的内容"), context()).isMatched());
    }
}
