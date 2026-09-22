package com.knowledge.common.exception;

import com.knowledge.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ThrowUtil 单测：条件成立按错误码抛出（默认 message / detail 覆盖）；条件不成立不抛。
 *
 * @author cxxl
 */
class ThrowUtilTest {

    @Test
    void throwIfShouldThrowWithDefaultMessage() {
        KnowledgeException e = assertThrows(KnowledgeException.class,
                () -> ThrowUtil.throwIf(true, ErrorCode.LOGIN_FAILED));
        assertEquals(ErrorCode.LOGIN_FAILED.getCode(), e.getCode());
        assertEquals(ErrorCode.LOGIN_FAILED.getMessage(), e.getMessage());
    }

    @Test
    void throwIfShouldThrowWithDetailMessage() {
        KnowledgeException e = assertThrows(KnowledgeException.class,
                () -> ThrowUtil.throwIf(true, ErrorCode.USERNAME_EXISTS, "用户名 admin 已存在"));
        assertEquals(ErrorCode.USERNAME_EXISTS.getCode(), e.getCode());
        assertEquals("用户名 admin 已存在", e.getMessage());
    }

    @Test
    void throwIfShouldPassWhenConditionFalse() {
        assertDoesNotThrow(() -> ThrowUtil.throwIf(false, ErrorCode.LOGIN_FAILED));
        assertDoesNotThrow(() -> ThrowUtil.throwIf(false, ErrorCode.LOGIN_FAILED, "不会抛"));
    }
}
