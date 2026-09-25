package com.knowledge.common.enums.knowledge;

/**
 * 审计动作类型（kb_audit_log.action_type）。
 *
 * @author cxxl
 */
public enum AuditActionType {

    /** 创建 */
    CREATE,

    /** 更新 */
    UPDATE,

    /** 停用 */
    DISABLE,

    /** 启用 */
    ENABLE,

    /** 策略绑定/解绑 */
    BIND,

    /** 逻辑删除 */
    DELETE,

    /** 索引发布（step-13 B08） */
    PUBLISH_INDEX,

    /** 索引回退（step-13 B08） */
    ROLLBACK_INDEX,

    /** 索引候选回收（step-13 B08） */
    RECYCLE_INDEX,

    /** 检索规则选优发布（step-14 B09/B10） */
    RETRIEVAL_RULE_PUBLISH,

    /** 用户管理：新增账号 */
    USER_CREATE,

    /** 用户管理：更新账号（角色变更 / 状态变更 / 重置密码） */
    USER_UPDATE,

    /** 用户管理：停用账号 */
    USER_DISABLE,

    /** 用户管理：启用账号 */
    USER_ENABLE,

    /** 用户管理：删除账号（逻辑删除） */
    USER_DELETE
}
