package com.knowledge.biz.domain;

import com.knowledge.common.domain.entity.KnowledgeBase;
import com.knowledge.common.domain.rules.KnowledgeBaseRules;
import com.knowledge.common.enums.knowledge.KnowledgeBaseStatus;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 状态规则纯单测（AIR/BCDE：合法路径 + 错误路径 + 边界 null）。
 *
 * @author cxxl
 */
class KnowledgeBaseRulesTest {

    private KnowledgeBase kb(Integer status) {
        KnowledgeBase k = new KnowledgeBase();
        k.setStatus(status);
        return k;
    }

    @Test
    void disableWhenActiveShouldPass() {
        assertDoesNotThrow(() -> KnowledgeBaseRules.checkCanDisable(kb(KnowledgeBaseStatus.ACTIVE.getCode())));
    }

    @Test
    void disableWhenDisabledShouldThrow() {
        KnowledgeException e = assertThrows(KnowledgeException.class,
                () -> KnowledgeBaseRules.checkCanDisable(kb(KnowledgeBaseStatus.DISABLED.getCode())));
        assertEquals(ErrorCode.KB_STATUS_ILLEGAL, e.getErrorCode());
    }

    @Test
    void disableWhenStatusNullShouldThrow() {
        assertThrows(KnowledgeException.class, () -> KnowledgeBaseRules.checkCanDisable(kb(null)));
    }

    @Test
    void enableWhenDisabledShouldPass() {
        assertDoesNotThrow(() -> KnowledgeBaseRules.checkCanEnable(kb(KnowledgeBaseStatus.DISABLED.getCode())));
    }

    @Test
    void enableWhenActiveShouldThrow() {
        KnowledgeException e = assertThrows(KnowledgeException.class,
                () -> KnowledgeBaseRules.checkCanEnable(kb(KnowledgeBaseStatus.ACTIVE.getCode())));
        assertEquals(ErrorCode.KB_STATUS_ILLEGAL, e.getErrorCode());
    }

    @Test
    void enableWhenStatusNullShouldThrow() {
        assertThrows(KnowledgeException.class, () -> KnowledgeBaseRules.checkCanEnable(kb(null)));
    }

    @Test
    void submitWhenActiveShouldPass() {
        assertDoesNotThrow(() -> KnowledgeBaseRules.checkCanSubmit(kb(KnowledgeBaseStatus.ACTIVE.getCode())));
    }

    @Test
    void submitWhenDisabledShouldThrow() {
        KnowledgeException e = assertThrows(KnowledgeException.class,
                () -> KnowledgeBaseRules.checkCanSubmit(kb(KnowledgeBaseStatus.DISABLED.getCode())));
        assertEquals(ErrorCode.KB_NOT_ACTIVE, e.getErrorCode());
    }

    @Test
    void submitWhenStatusNullShouldThrow() {
        KnowledgeException e = assertThrows(KnowledgeException.class,
                () -> KnowledgeBaseRules.checkCanSubmit(kb(null)));
        assertEquals(ErrorCode.KB_NOT_ACTIVE, e.getErrorCode());
    }
}
