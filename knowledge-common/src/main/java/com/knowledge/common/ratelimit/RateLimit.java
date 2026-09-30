package com.knowledge.common.ratelimit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口级限流：按客户端 IP 计数的令牌桶，窗口内超过 {@link #capacity()} 次即拒绝。
 *
 * @author cxxl
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /** 窗口内允许的请求次数 */
    int capacity() default 20;

    /** 窗口长度（秒） */
    int periodSeconds() default 60;

    /** 触发限流时的提示语；留空时用错误码默认文案 */
    String message() default "";
}
