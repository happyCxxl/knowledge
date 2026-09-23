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
    RECYCLE_INDEX
}
