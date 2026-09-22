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
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 目录处置规则单测：整页聚合阈值（PDF）/连续窗口（Word）/非目录页不受影响/三档处置。
 *
 * @author cxxl
 */
class TocRuleTest {

    private final TocRule rule = new TocRule();

    private ViewElement tocElement(Integer page) {
        ViewElement element = new ViewElement();
        element.setElementId("n-" + page);
        element.setType("PARAGRAPH");
        element.setStatus(ViewElementStatus.NORMAL.name());
        element.setMarks(List.of(ElementMark.TOC_LINE.name()));
        element.setPage(page);
        element.setRawText("第一章 总则 ...... 1");
        element.setDisplayText("第一章 总则 ...... 1");
        element.setNormalizedText("第一章 总则 ...... 1");
        return element;
    }

    private RuleContext context(Map<Integer, Integer> tocCount,
                                Set<String> tocRunIds) {
        PreprocessStrategy strategy = PreprocessStrategy.defaultStrategy();
        strategy.rule(PreprocessRule.TOC).setAction("MARK");
        RuleContext context = new RuleContext();
        context.setStrategy(strategy);
        context.setProperties(new PreprocessProperties());
        context.setTocLineCountByPage(tocCount);
        context.setTocRunElementIds(tocRunIds);
        context.setRepeatedPageNumbers(new HashSet<>());
        context.setNoisePageNumbers(new HashSet<>());
        return context;
    }

    @Test
    void tocPageShouldMarkByThreshold() {
        Map<Integer, Integer> counts = new HashMap<>(Map.of(5, 3));
        ViewElement element = tocElement(5);

        var outcome = rule.apply(element, context(counts, new HashSet<>()));

        assertTrue(outcome.isMatched());
        assertEquals(ViewElementStatus.MARKED_TOC.name(), element.getStatus());
    }

    @Test
    void belowThresholdShouldNotDispose() {
        Map<Integer, Integer> counts = new HashMap<>(Map.of(5, 2));
        ViewElement element = tocElement(5);

        assertFalse(rule.apply(element, context(counts, new HashSet<>())).isMatched());
        assertEquals(ViewElementStatus.NORMAL.name(), element.getStatus());
    }

    @Test
    void wordWindowShouldDispose() {
        ViewElement element = tocElement(null);
        Set<String> runIds = new HashSet<>(Set.of("n-null"));

        var outcome = rule.apply(element, context(new HashMap<>(), runIds));

        assertTrue(outcome.isMatched());
        assertEquals(ViewElementStatus.MARKED_TOC.name(), element.getStatus());
    }

    @Test
    void excludeShouldNullNormalizedText() {
        ViewElement element = tocElement(5);
        assertEquals(ViewElementStatus.EXCLUDED_TOC.name(), element.getStatus());
        assertNull(element.getNormalizedText());
    }

    @Test
    void nonTocElementShouldNotMatch() {
        ViewElement element = tocElement(5);
        element.setMarks(null);

        assertFalse(rule.apply(element, context(new HashMap<>(Map.of(5, 3)), new HashSet<>())).isMatched());
    }
}
