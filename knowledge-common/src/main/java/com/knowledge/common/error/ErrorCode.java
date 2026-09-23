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

    /** 知识库不存在（或已逻辑删除） */
    KB_NOT_FOUND(40401, "知识库不存在"),

    /** 知识库状态不合法（非法启停/删除/状态迁移） */
    KB_STATUS_ILLEGAL(40402, "知识库状态不合法"),

    /** 文件不存在（文件档案查不到或取流失败） */
    FILE_NOT_FOUND(40410, "文件不存在"),

    /** 未传幂等键 */
    REQUEST_ID_MISSING(40420, "缺少幂等键"),

    /** 知识库未启用（不可提交） */
    KB_NOT_ACTIVE(40421, "知识库未启用，不可提交文档"),

    /** 任务进行中（同环节已有排队/执行中任务，拒绝重复触发） */
    TASK_ALREADY_PENDING(40431, "任务进行中，请勿重复触发"),

    /** 文件结果不存在 */
    FILE_RESULT_NOT_FOUND(40432, "文件结果不存在"),

    /** 策略版本不存在或未启用 */
    STRATEGY_VERSION_NOT_FOUND(40433, "策略版本不存在或未启用"),

    /** 解析已成功或部分成功，禁止重跑 */
    PARSE_ALREADY_SUCCEEDED(40437, "解析已成功或部分成功，无需重跑"),

    /** 系统异常 */
    SYSTEM_ERROR(40500, "系统异常，请稍后重试");

    private final int code;

    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
