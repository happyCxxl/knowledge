package com.knowledge.common.enums.knowledge;

import cn.hutool.core.util.StrUtil;

/**
 * 审计对象类型（kb_audit_log.object_type）。
 *
 * <p>原先这些码值散在各业务 service 的私有常量里（`AUDIT_OBJECT_TYPE = "KNOWLEDGE_BASE"`），
 * 首页行为记录要按类型反查对象名，需要一处集中的口径。
 *
 * @author cxxl
 */
public enum AuditObjectType {

    /** 知识库 */
    KNOWLEDGE_BASE("KNOWLEDGE_BASE", "知识库"),

    /** 索引版本 */
    INDEX_VERSION("INDEX_VERSION", "索引版本"),

    /** 账号（用户管理） */
    KB_USER("KB_USER", "账号");

    /** 码值（与库表 object_type 列一致） */
    private final String key;

    /** 中文名（首页行为记录展示用；后端下发，避免前端再维护映射表） */
    private final String label;

    AuditObjectType(String key, String label) {
        this.key = key;
        this.label = label;
    }

    /** 码值 */
    public String key() {
        return key;
    }

    /** 中文名 */
    public String label() {
        return label;
    }

    /** 按码值查找；未识别返回 null（调用方兜底成码值本身） */
    public static AuditObjectType of(String key) {
        if (StrUtil.isBlank(key)) {
            return null;
        }
        for (AuditObjectType type : values()) {
            if (type.key.equalsIgnoreCase(key)) {
                return type;
            }
        }
        return null;
    }
}
