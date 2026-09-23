package com.knowledge.worker.chunking.strategy;

import com.knowledge.common.enums.chunk.ChunkAlgorithm;
import com.knowledge.common.enums.chunk.ChunkRoute;
import com.knowledge.common.enums.chunk.PipelineKey;
import com.knowledge.worker.chunking.ChunkProperties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 切片策略解析器单测：结构化解析补全默认 / 空与非法回退内置默认 / pipeline 单键配置映射。
 *
 * @author cxxl
 */
class ChunkStrategyParserTest {

    private final ChunkStrategyParser parser = new ChunkStrategyParser(new ChunkProperties());

    @Test
    void structuredConfigShouldFillMissingDefaults() {
        ChunkStrategy strategy = parser.parse("{\"routes\":{\"body\":{\"algorithm\":\"title-boundary\"}}}");

        assertEquals(ChunkAlgorithm.BODY_TITLE_BOUNDARY, strategy.routeAlgorithm(ChunkRoute.BODY));
        // 其余路由补默认算法 + 参数
        assertEquals(ChunkAlgorithm.TABLE_ROW_SLICE, strategy.routeAlgorithm(ChunkRoute.TABLE));
        assertEquals(ChunkAlgorithm.IMAGE_CAPTION_PLACEHOLDER, strategy.routeAlgorithm(ChunkRoute.IMAGE));
        assertEquals(ChunkAlgorithm.FALLBACK_RECURSIVE, strategy.routeAlgorithm(ChunkRoute.FALLBACK));
        // 所选算法的默认参数被补齐（title-boundary → maxLen=3000；row-slice → groupThreshold=30）
        assertEquals("3000", strategy.route(ChunkRoute.BODY).strParam("maxLen", ""));
        assertEquals(30, strategy.route(ChunkRoute.TABLE).intParam("groupThreshold", -1));
        // 流程层默认
        assertEquals("ON", strategy.pipelineValue(PipelineKey.PARENT_CHILD, ""));
        assertEquals("ON", strategy.pipelineValue(PipelineKey.TITLE_IN_CONTENT, ""));
        assertEquals("OFF", strategy.pipelineValue(PipelineKey.TABLE_IN_BODY_FLOW, ""));
        assertEquals("300", strategy.pipelineValue(PipelineKey.MIN_MERGE_LEN, ""));
    }

    @Test
    void seedSnapshotWithAllRoutesShouldApplyEveryRoute() {
        String seed = "{\"routes\":{"
                + "\"body\":{\"algorithm\":\"fixed-window\",\"params\":{\"len\":\"500\",\"overlap\":\"100\"}},"
                + "\"table\":{\"algorithm\":\"context-merged\",\"params\":{\"leadMaxLen\":\"200\",\"groupThreshold\":\"30\",\"groupSize\":\"3\"}},"
                + "\"image\":{\"algorithm\":\"caption-placeholder\"},"
                + "\"fallback\":{\"algorithm\":\"fixed-window\",\"params\":{\"len\":\"500\",\"overlap\":\"100\"}}"
                + "},"
                + "\"pipeline\":{\"titlePathMaxLevel\":\"3\",\"parentChild\":\"ON\",\"tableInBodyFlow\":\"OFF\","
                + "\"minMergeLen\":\"200\",\"structureOverlap\":\"0\",\"titleInContent\":\"ON\"}"
                + "}";

        ChunkStrategy strategy = parser.parse(seed);

        assertEquals(ChunkAlgorithm.BODY_FIXED_WINDOW, strategy.routeAlgorithm(ChunkRoute.BODY));
        assertEquals(500, strategy.route(ChunkRoute.BODY).intParam("len", -1));
        assertEquals(100, strategy.route(ChunkRoute.BODY).intParam("overlap", -1));
        assertEquals(ChunkAlgorithm.TABLE_CONTEXT_MERGED, strategy.routeAlgorithm(ChunkRoute.TABLE));
        assertEquals(200, strategy.route(ChunkRoute.TABLE).intParam("leadMaxLen", -1));
        assertEquals(ChunkAlgorithm.IMAGE_CAPTION_PLACEHOLDER, strategy.routeAlgorithm(ChunkRoute.IMAGE));
        assertEquals(ChunkAlgorithm.FALLBACK_FIXED_WINDOW, strategy.routeAlgorithm(ChunkRoute.FALLBACK));
        assertEquals("200", strategy.pipelineValue(PipelineKey.MIN_MERGE_LEN, ""));
        assertEquals("ON", strategy.pipelineValue(PipelineKey.TITLE_IN_CONTENT, ""));
        assertEquals("OFF", strategy.pipelineValue(PipelineKey.TABLE_IN_BODY_FLOW, ""));
    }

    @Test
    void blankSnapshotShouldFallbackToBuiltinDefault() {
        ChunkStrategy strategy = parser.parse(null);
        assertNotNull(strategy);
        assertEquals("chunk-hybrid", strategy.getName());
        assertEquals("v1", strategy.getVersion());
        assertEquals(ChunkAlgorithm.BODY_PARAGRAPH_AGGREGATE, strategy.routeAlgorithm(ChunkRoute.BODY));
    }

    @Test
    void invalidSnapshotShouldFallbackToBuiltinDefault() {
        ChunkStrategy strategy = parser.parse("not-a-json");
        assertEquals("chunk-hybrid", strategy.getName());
        assertEquals(ChunkAlgorithm.BODY_PARAGRAPH_AGGREGATE, strategy.routeAlgorithm(ChunkRoute.BODY));
    }

    @Test
    void pipelineOnlyConfigShouldMapParentChild() {
        ChunkStrategy strategy = parser.parse("{\"pipeline\":{\"parentChild\":\"OFF\"}}");

        assertEquals("OFF", strategy.pipelineValue(PipelineKey.PARENT_CHILD, ""));
        assertEquals(ChunkAlgorithm.BODY_PARAGRAPH_AGGREGATE, strategy.routeAlgorithm(ChunkRoute.BODY));
    }

    @Test
    void parseConfigShouldLeaveNameVersionNull() {
        ChunkStrategy strategy = parser.parseConfig("{\"routes\":{}}");

        assertNull(strategy.getName());
        assertNull(strategy.getVersion());
        assertEquals(ChunkAlgorithm.BODY_PARAGRAPH_AGGREGATE, strategy.routeAlgorithm(ChunkRoute.BODY));
    }
}
