package com.knowledge.worker.preprocessing.strategy;

import com.knowledge.common.enums.preprocess.PreprocessParam;
import com.knowledge.common.enums.preprocess.PreprocessRule;
import com.knowledge.worker.preprocessing.PreprocessProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 预处理策略解析器单测（step-10）：结构化解析补全默认 / 空与非法回退内置默认 / custom 解析。
 *
 * @author cxxl
 */
class PreprocessStrategyParserTest {

    private final PreprocessStrategyParser parser = new PreprocessStrategyParser(new PreprocessProperties());

    @Test
    void structuredConfigShouldFillMissingDefaults() {
        PreprocessStrategy strategy = parser.parse("{\"rules\":{\"headerFooter\":{\"action\":\"EXCLUDE\"}}}");

        assertEquals("EXCLUDE", strategy.action(PreprocessRule.HEADER_FOOTER, "MARK"));
        assertEquals("MARK", strategy.action(PreprocessRule.TOC, "MARK"));
        assertEquals("MARK", strategy.action(PreprocessRule.NOISE, "MARK"));
        assertTrue(strategy.enabled(PreprocessRule.REPEAT, false));
        assertTrue(strategy.enabled(PreprocessRule.FIELD, false));
        assertTrue(strategy.enabled(PreprocessRule.TIDY, false));
        assertTrue(strategy.enabled(PreprocessRule.ENCODING, false));
        // 参数补默认（值源 PreprocessProperties）
        assertEquals(3, strategy.intParam(PreprocessRule.TOC, PreprocessParam.TOC_MIN_LINES_PER_PAGE, 0));
        assertTrue(strategy.boolParam(PreprocessRule.TIDY, PreprocessParam.TIDY_URLS, false));
        assertTrue(strategy.boolParam(PreprocessRule.FIELD, PreprocessParam.FIELD_AMOUNT, false));
        // custom 默认
        assertEquals(PreprocessStrategy.ON, strategy.getCustom().getEnabled());
        assertTrue(strategy.customRules().isEmpty());
    }

    @Test
    void blankSnapshotShouldFallbackToBuiltinDefault() {
        PreprocessStrategy strategy = parser.parse(null);

        assertNotNull(strategy);
        assertEquals("preproc-default", strategy.getName());
        assertEquals("v1", strategy.getVersion());
        assertEquals("MARK", strategy.action(PreprocessRule.HEADER_FOOTER, ""));
    }

    @Test
    void invalidSnapshotShouldFallbackToBuiltinDefault() {
        PreprocessStrategy strategy = parser.parse("not-a-json");

        assertEquals("preproc-default", strategy.getName());
        assertEquals("MARK", strategy.action(PreprocessRule.HEADER_FOOTER, ""));
    }

    @Test
    void customRulesShouldParse() {
        PreprocessStrategy strategy = parser.parse(
                "{\"custom\":{\"enabled\":\"ON\",\"rules\":["
                        + "{\"pattern\":\"版权所有\\\\s*©.*\",\"action\":\"REMOVE\"},"
                        + "{\"pattern\":\"(电话[:：])(\\\\d+)\",\"action\":\"REPLACE\",\"replacement\":\"$1***\"}]}}");

        assertEquals(2, strategy.customRules().size());
        assertEquals("版权所有\\s*©.*", strategy.customRules().get(0).getPattern());
        assertEquals("REMOVE", strategy.customRules().get(0).getAction());
        assertEquals("$1***", strategy.customRules().get(1).getReplacement());
    }

    @Test
    void customOffShouldYieldEmptyRules() {
        PreprocessStrategy strategy = parser.parse(
                "{\"custom\":{\"enabled\":\"OFF\",\"rules\":[{\"pattern\":\"x+\",\"action\":\"REMOVE\"}]}}");

        assertTrue(strategy.customRules().isEmpty());
    }

    @Test
    void parseConfigShouldLeaveNameVersionNull() {
        PreprocessStrategy strategy = parser.parseConfig("{\"rules\":{}}");

        assertNull(strategy.getName());
        assertNull(strategy.getVersion());
        assertEquals("MARK", strategy.action(PreprocessRule.HEADER_FOOTER, ""));
    }
}
