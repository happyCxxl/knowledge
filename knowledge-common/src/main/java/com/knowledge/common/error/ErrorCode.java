package com.knowledge.common.error;

import lombok.Getter;

/**
 * 统一错误码。
 *
 * <p>登记规则：新错误码必须在此枚举登记；编码分段：40001 参数、401xx 认证、404xx 业务、40500 系统兜底。
 *
 * @author cxxl
 */
@Getter
public enum ErrorCode {

    /** 参数错误 */
    PARAM_INVALID(40001, "参数错误"),

    /** 未认证或令牌无效 */
    UNAUTHORIZED(40101, "未认证或令牌无效"),

    /** 用户名已存在 */
    USERNAME_EXISTS(40102, "用户名已存在"),

    /** 用户名或密码错误 */
    LOGIN_FAILED(40103, "用户名或密码错误"),

    /** 系统异常 */
    SYSTEM_ERROR(40500, "系统异常，请稍后重试");

    private final int code;

    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
