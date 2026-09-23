package com.knowledge.worker.embedding.strategy;

import com.knowledge.common.enums.embed.EmbedMetric;
import com.knowledge.common.enums.embed.EmbeddingModel;
import com.knowledge.model.catalog.StaticModelCatalog;
import com.knowledge.worker.embedding.EmbedProperties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 向量化策略解析器单测：默认补齐 / 目录冗余回填 / 空与非法回退内置默认 / parseConfig 不设 name/version。
 *
 * @author cxxl
 */
class EmbedStrategyParserTest {

    private final EmbedStrategyParser parser = new EmbedStrategyParser(new EmbedProperties(), new StaticModelCatalog());

    @Test
    void configShouldFillDefaultsAndStampCatalog() {
        EmbedStrategy strategy = parser.parse("{\"model\":\"text-embedding-v4\",\"batchSize\":64,\"cacheEnabled\":\"OFF\"}");

        assertEquals("text-embedding-v4", strategy.getModel());
        assertEquals("{content}", strategy.getDocTemplate());
        assertEquals("{query}", strategy.getQueryTemplate());
        assertEquals(64, strategy.getBatchSize());
        assertEquals(30000, strategy.getTimeoutMs());
        assertEquals(2, strategy.getMaxRetries());
        assertEquals("OFF", strategy.getCacheEnabled());
        assertEquals("ON", strategy.getSkipEmpty());
        assertEquals("OFF", strategy.getIncludeParent());
        // 目录冗余回填
        assertEquals(1024, strategy.getDimension());
        assertEquals(EmbedMetric.COSINE.key(), strategy.getMetric());
        assertTrue(strategy.getNormalized());
        assertEquals(8192, strategy.getContextWindowTokens());
        assertEquals(64, strategy.getBatchLimit());
    }

    @Test
    void blankModelShouldFallbackToFirstEnabledModel() {
        EmbedStrategy strategy = parser.parse("{\"batchSize\":16}");

        assertEquals(EmbeddingModel.firstEnabled().key(), strategy.getModel());
        assertEquals(EmbeddingModel.firstEnabled().dimension(), strategy.getDimension());
        assertEquals(16, strategy.getBatchSize());
    }

    @Test
    void blankSnapshotShouldFallbackToBuiltinDefault() {
        EmbedStrategy strategy = parser.parse(null);
        assertNotNull(strategy);
        assertEquals("embed-default", strategy.getName());
        assertEquals("v1", strategy.getVersion());
        assertEquals(EmbeddingModel.firstEnabled().key(), strategy.getModel());
        assertTrue(strategy.cacheOn());
    }

    @Test
    void invalidSnapshotShouldFallbackToBuiltinDefault() {
        EmbedStrategy strategy = parser.parse("not-a-json");
        assertEquals("embed-default", strategy.getName());
        assertEquals(EmbeddingModel.firstEnabled().key(), strategy.getModel());
    }

    @Test
    void disabledModelShouldStillParseAndStampCatalog() {
        // 解析不校验启用（启用校验在保存/触发侧），目录冗余仍按目录回填
        EmbedStrategy strategy = parser.parse("{\"model\":\"text-embedding-v3\"}");

        assertEquals("text-embedding-v3", strategy.getModel());
        assertEquals(1024, strategy.getDimension());
        assertEquals(8192, strategy.getContextWindowTokens());
    }

    @Test
    void parseConfigShouldLeaveNameVersionNull() {
        EmbedStrategy strategy = parser.parseConfig("{\"model\":\"text-embedding-v4\"}");

        assertNull(strategy.getName());
        assertNull(strategy.getVersion());
        assertEquals("text-embedding-v4", strategy.getModel());
    }
}
