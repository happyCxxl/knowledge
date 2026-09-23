package com.knowledge.worker.chunking.strategy;

import com.knowledge.common.enums.chunk.ChunkRoute;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.worker.chunking.ChunkProperties;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 切片算法规格单测：保存校验口径（支持集/参数范围/不变量/互斥/非法结构）+ 支持集判定 + 默认参数补齐。
 *
 * @author cxxl
 */
class ChunkAlgorithmSpecTest {

    private Map<String, Object> config(String json) {
        return JsonUtil.toMap(json);
    }

    @Test
    void unsupportedAlgorithmShouldBeRejected() {
        String error = ChunkAlgorithmSpec.validate(config("{\"routes\":{\"body\":{\"algorithm\":\"semantic\"}}}"));

        assertNotNull(error);
        assertTrue(error.contains("semantic"));
    }

    @Test
    void paramOutOfRangeShouldBeRejected() {
        String error = ChunkAlgorithmSpec.validate(config(
                "{\"routes\":{\"body\":{\"algorithm\":\"paragraph-aggregate\",\"params\":{\"targetMaxLen\":\"99999\"}}}}"));

        assertNotNull(error);
        assertTrue(error.contains("参数越界"));
    }

    @Test
    void softBelowTargetShouldBeRejected() {
        String error = ChunkAlgorithmSpec.validate(config(
                "{\"routes\":{\"body\":{\"algorithm\":\"paragraph-aggregate\",\"params\":{\"targetMaxLen\":\"800\",\"softMaxLen\":\"500\"}}}}"));

        assertNotNull(error);
        assertTrue(error.contains("softMaxLen"));
    }

    @Test
    void contextMergedWithTableInBodyFlowShouldBeRejected() {
        String error = ChunkAlgorithmSpec.validate(config(
                "{\"routes\":{\"table\":{\"algorithm\":\"context-merged\"}},\"pipeline\":{\"tableInBodyFlow\":\"ON\"}}"));

        assertNotNull(error);
        assertTrue(error.contains("同时启用"));
    }

    @Test
    void invalidStructureShouldBeRejected() {
        String error = ChunkAlgorithmSpec.validate(config("{\"routes\":\"not-a-map\"}"));

        assertNotNull(error);
        assertTrue(error.contains("routes"));
    }

    @Test
    void validConfigShouldPass() {
        String error = ChunkAlgorithmSpec.validate(config(
                "{\"routes\":{\"body\":{\"algorithm\":\"title-boundary\",\"params\":{\"maxLen\":\"3000\"}}},\"pipeline\":{\"titleInContent\":\"ON\"}}"));

        assertNull(error);
    }

    @Test
    void supportedSetShouldFollowCatalog() {
        assertTrue(ChunkAlgorithmSpec.isSupported(ChunkRoute.BODY, "paragraph-aggregate"));
        assertFalse(ChunkAlgorithmSpec.isSupported(ChunkRoute.BODY, "semantic"));
        assertFalse(ChunkAlgorithmSpec.isSupported(ChunkRoute.BODY, "unknown-algorithm"));
    }

    @Test
    void defaultParamsShouldFillPerAlgorithm() {
        Map<String, String> defaults = ChunkAlgorithmSpec.defaultParams(ChunkRoute.TABLE, "row-group",
                new ChunkProperties());

        assertEquals("5", defaults.get("groupSize"));
        assertEquals("600", defaults.get("maxLen"));
    }
}
