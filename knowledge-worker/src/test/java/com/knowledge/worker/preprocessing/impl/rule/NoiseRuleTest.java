package com.knowledge.worker.preprocessing.impl.rule;
import com.knowledge.common.enums.preprocess.ViewElementStatus;

import com.knowledge.common.domain.preprocess.ViewElement;
import com.knowledge.common.enums.preprocess.PreprocessRule;
import com.knowledge.worker.preprocessing.PreprocessProperties;
import com.knowledge.worker.preprocessing.strategy.PreprocessStrategy;
import com.knowledge.worker.preprocessing.rule.RuleContext;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 噪声处置规则单测：MARK/EXCLUDE 两档；非噪声页不命中。
 *
 * @author cxxl
 */
class NoiseRuleTest {

    private final NoiseRule rule = new NoiseRule();

    private ViewElement element() {
        ViewElement element = new ViewElement();
        element.setElementId("n-1");
        element.setType("PARAGRAPH");
        element.setPage(9);
        element.setStatus(ViewElementStatus.NORMAL.name());
        element.setRawText("噪声页内容");
        element.setDisplayText("噪声页内容");
        element.setNormalizedText("噪声页内容");
        return element;
    }

    private RuleContext context(String action) {
        PreprocessStrategy strategy = PreprocessStrategy.defaultStrategy();
        strategy.rule(PreprocessRule.NOISE).setAction(action);
        RuleContext context = new RuleContext();
        context.setStrategy(strategy);
        context.setProperties(new PreprocessProperties());
        context.setTocLineCountByPage(new HashMap<>());
        context.setTocRunElementIds(new HashSet<>());
        context.setRepeatedPageNumbers(new HashSet<>());
        context.setNoisePageNumbers(Set.of(9));
        return context;
    }

    @Test
    void markShouldSetNoiseStatus() {
        ViewElement element = element();
        var outcome = rule.apply(element, context("MARK"));

        assertTrue(outcome.isMatched());
        assertEquals(ViewElementStatus.NOISE.name(), element.getStatus());
        assertEquals("噪声页内容", element.getNormalizedText());
    }

    @Test
    void excludeShouldNullNormalizedText() {
        ViewElement element = element();
        rule.apply(element, context("EXCLUDE"));

        assertEquals(ViewElementStatus.EXCLUDED_NOISE.name(), element.getStatus());
        assertNull(element.getNormalizedText());
    }

    @Test
    void nonNoisePageShouldNotMatch() {
        ViewElement element = element();
        element.setPage(1);

        assertFalse(rule.apply(element, context("MARK")).isMatched());
        assertEquals(ViewElementStatus.NORMAL.name(), element.getStatus());
    }
}
