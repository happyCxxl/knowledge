package com.knowledge.worker.indexing;

import com.knowledge.common.enums.index.IndexShape;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import com.knowledge.common.utils.JsonUtil;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 组合快照格式断言单测（step-13 B08，2026-09 冒烟缺陷根治收敛）：
 * stageStrategies 三环节策略维度是组合的身份与血缘载体——旧口径快照（无该维度）
 * 由唯一断言点 requireStageStrategies 显式拒绝（40448），业务路径不再散点防御。
 *
 * @author cxxl
 */
class ComboSnapshotTest {

    @Test
    void hasCompleteStageStrategiesShouldBeTrueForFactoryCombo() {
        assertTrue(ComboSnapshot.of("preproc-default-v1", "chunk-hybrid-v1", "embed-default-v1")
                .hasCompleteStageStrategies());
    }

    @Test
    void hasCompleteStageStrategiesShouldBeFalseForLegacyShapedSnapshot() {
        assertFalse(new ComboSnapshot().hasCompleteStageStrategies());

        ComboSnapshot nullMap = new ComboSnapshot();
        nullMap.setStageStrategies(null);
        assertFalse(nullMap.hasCompleteStageStrategies());
    }

    @Test
    void hasCompleteStageStrategiesShouldBeFalseWhenAnyStageMissing() {
        assertFalse(ComboSnapshot.of(null, "chunk-hybrid-v1", "embed-default-v1").hasCompleteStageStrategies());
        assertFalse(ComboSnapshot.of("preproc-default-v1", "", "embed-default-v1").hasCompleteStageStrategies());
        assertFalse(ComboSnapshot.of("preproc-default-v1", "chunk-hybrid-v1", null).hasCompleteStageStrategies());
    }

    @Test
    void requireStageStrategiesShouldThrowExplicitErrorForLegacySnapshot() {
        KnowledgeException ex = assertThrows(KnowledgeException.class,
                () -> new ComboSnapshot().requireStageStrategies());

        assertEquals(ErrorCode.INDEX_COMBO_SNAPSHOT_LEGACY.getCode(), ex.getCode());
    }

    @Test
    void requireStageStrategiesShouldPassForCompleteCombo() {
        assertDoesNotThrow(() -> ComboSnapshot.of("preproc-default-v1", "chunk-hybrid-v1", "embed-default-v1")
                .requireStageStrategies());
    }

    @Test
    void isListScopeShouldReflectScopeMode() {
        ComboSnapshot combo = new ComboSnapshot();
        assertFalse(combo.isListScope());

        combo.setFileScopeMode("LIST");
        assertTrue(combo.isListScope());
    }

    @Test
    void shapeShouldRoundTripAsEnumName() {
        String json = Objects.requireNonNull(
                JsonUtil.toJsonStr(ComboSnapshot.of("preproc-default-v1", "chunk-hybrid-v1", "embed-default-v1")));

        assertTrue(json.contains("\"FULL_VECTOR\""), json);
        ComboSnapshot parsed = Objects.requireNonNull(JsonUtil.toObject(json, ComboSnapshot.class));
        assertEquals(IndexShape.FULL_VECTOR, parsed.getShape());
    }
}
