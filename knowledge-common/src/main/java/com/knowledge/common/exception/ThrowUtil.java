package com.knowledge.common.exception;

import com.knowledge.common.error.ErrorCode;

import java.util.Objects;

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

    /**
     * 条件成立时先执行补充动作，再按错误码抛出（使用默认消息）。
     *
     * <p>补充动作在抛出之前执行；它自身抛出的异常不替换业务异常，而是作为 suppressed 挂在业务异常上。
     *
     * @param condition   为 true 时抛出
     * @param errorCode   错误码
     * @param beforeThrow 抛出前的补充动作，不可为 null
     */
    public static void throwIf(boolean condition, ErrorCode errorCode, Runnable beforeThrow) {
        if (!condition) {
            return;
        }
        KnowledgeException failure = new KnowledgeException(errorCode);
        runBeforeThrow(beforeThrow, failure);
        throw failure;
    }

    /**
     * 条件成立时先执行补充动作，再按错误码抛出，detail 覆盖默认消息。
     *
     * @param condition   为 true 时抛出
     * @param errorCode   错误码
     * @param errMessage  补充原因
     * @param beforeThrow 抛出前的补充动作，不可为 null
     */
    public static void throwIf(boolean condition, ErrorCode errorCode, String errMessage,
            Runnable beforeThrow) {
        if (!condition) {
            return;
        }
        KnowledgeException failure = new KnowledgeException(errorCode, errMessage);
        runBeforeThrow(beforeThrow, failure);
        throw failure;
    }

    /** 补充动作失败不掩盖业务异常：降级为 suppressed */
    private static void runBeforeThrow(Runnable beforeThrow, KnowledgeException failure) {
        Objects.requireNonNull(beforeThrow, "beforeThrow 不可为 null");
        try {
            beforeThrow.run();
        } catch (RuntimeException e) {
            failure.addSuppressed(e);
        }
    }
}
