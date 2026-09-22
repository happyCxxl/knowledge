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

    /** 逻辑删除 */
    DELETE
}
