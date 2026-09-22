package com.knowledge.common.exception;

import com.knowledge.common.error.ErrorCode;

/**
 * 校验守卫工具：条件成立时抛出统一业务异常。
 *
 * @author cxxl
 */
public final class ThrowUtil {

    private ThrowUtil() {
    }

    /**
     * 条件成立时按错误码抛出（使用默认消息）。
     *
     * @param condition 为 true 时抛出
     * @param errorCode 错误码
     */
    public static void throwIf(boolean condition, ErrorCode errorCode) {
        if (condition) {
            throw new KnowledgeException(errorCode);
        }
    }

    /**
     * 条件成立时按错误码抛出，detail 覆盖默认消息。
     *
     * @param condition  为 true 时抛出
     * @param errorCode  错误码
     * @param errMessage 补充原因
     */
    public static void throwIf(boolean condition, ErrorCode errorCode, String errMessage) {
        if (condition) {
            throw new KnowledgeException(errorCode, errMessage);
        }
    }
}
