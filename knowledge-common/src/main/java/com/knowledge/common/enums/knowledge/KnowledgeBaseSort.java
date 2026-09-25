package com.knowledge.common.enums.knowledge;

/**
 * 知识库列表排序口径（kb_knowledge_base 分页列表）。
 *
 * <p>所有口径都优先把默认库（default_flag=1）排在最前，其后再按各自规则排序。
 *
 * @author cxxl
 */
public enum KnowledgeBaseSort {

    /** 默认：默认库最前，其余按 id 倒序（新建的在前） */
    DEFAULT,

    /** 最近更新：默认库最前，其余按 update_time 倒序 */
    UPDATED,

    /** 名称：默认库最前，其余按 name 升序 */
    NAME;

    /**
     * 按名称解析排序口径；空值或未知值回落 {@link #DEFAULT}（不抛异常，避免旧客户端报错）。
     *
     * @param name 排序口径名（大小写不敏感）
     * @return 排序口径
     */
    public static KnowledgeBaseSort of(String name) {
        if (name == null || name.isBlank()) {
            return DEFAULT;
        }
        for (KnowledgeBaseSort sort : values()) {
            if (sort.name().equalsIgnoreCase(name.trim())) {
                return sort;
            }
        }
        return DEFAULT;
    }
}
