package com.knowledge.worker.preprocessing.impl.rule;
import com.knowledge.common.enums.structure.ElementMark;
import com.knowledge.common.enums.preprocess.ViewElementStatus;

import com.knowledge.common.domain.preprocess.ViewElement;
import com.knowledge.common.enums.preprocess.PreprocessRule;
import com.knowledge.worker.preprocessing.PreprocessProperties;
import com.knowledge.worker.preprocessing.strategy.PreprocessStrategy;
import com.knowledge.worker.preprocessing.rule.RuleContext;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 重复处置规则单测：段级/页级标记 → 只留一份；OFF 关闭。
 *
 * @author cxxl
 */
class RepeatRuleTest {

    private final RepeatRule rule = new RepeatRule();

    private ViewElement element() {
        ViewElement element = new ViewElement();
        element.setElementId("n-1");
        element.setType("PARAGRAPH");
        element.setStatus(ViewElementStatus.NORMAL.name());
        element.setRawText("重复段落内容");
        element.setDisplayText("重复段落内容");
        element.setNormalizedText("重复段落内容");
        return element;
    }

    private RuleContext context(Set<Integer> repeatedPages) {
        PreprocessStrategy strategy = PreprocessStrategy.defaultStrategy();
        strategy.rule(PreprocessRule.REPEAT).setEnabled("ON");
        RuleContext context = new RuleContext();
        context.setStrategy(strategy);
        context.setProperties(new PreprocessProperties());
        context.setTocLineCountByPage(new HashMap<>());
        context.setTocRunElementIds(new HashSet<>());
        context.setRepeatedPageNumbers(repeatedPages);
        context.setNoisePageNumbers(new HashSet<>());
        return context;
    }

    @Test
    void repeatedSegmentMarkShouldExcludeFromContentFlow() {
        ViewElement element = element();
        element.setMarks(List.of(ElementMark.REPEATED_SEGMENT.name()));

        var outcome = rule.apply(element, context(new HashSet<>()));

        assertTrue(outcome.isMatched());
        assertEquals(ViewElementStatus.REPEATED.name(), element.getStatus());
        assertNull(element.getNormalizedText());
    }

    @Test
    void repeatedPageShouldExcludeFromContentFlow() {
        ViewElement element = element();
        element.setPage(7);

        var outcome = rule.apply(element, context(new HashSet<>(Set.of(7))));

        assertTrue(outcome.isMatched());
        assertEquals(ViewElementStatus.REPEATED.name(), element.getStatus());
        assertNull(element.getNormalizedText());
    }

    @Test
    void offShouldReportOff() {
        PreprocessStrategy strategy = PreprocessStrategy.defaultStrategy();
        strategy.rule(PreprocessRule.REPEAT).setEnabled(PreprocessStrategy.OFF);

        assertFalse(rule.enabledIn(strategy));
        assertTrue(rule.enabledIn(PreprocessStrategy.defaultStrategy()));
    }

    @Test
    void unmarkedShouldNotMatch() {
        assertFalse(rule.apply(element(), context(new HashSet<>())).isMatched());
    }
}
