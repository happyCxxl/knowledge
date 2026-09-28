package com.knowledge.common.enums.knowledge;

import java.util.Map;

/**
 * 审计动作的中文名（展示口径）。
 *
 * <p>为什么不把中文塞进 {@link AuditActionType}：那个枚举是**领域码值**，
 * 写入侧（各业务 service 与审计监听器）都用它；展示名只在读日志时要用。
 * 这里给两者搭一层映射，动作语义变更时只改这一处，各业务写入点不受影响。
 *
 * <p>作用域口径：码值是省事的（`CREATE` 这种通用词），同一个码在不同对象上语义不同
 * （知识库「创建」≠ 账号「新增」），所以这里给的是**知识库语境**下的说法；
 * 遇到账号类动作（USER_*）按各自语义单独列出。
 *
 * @author cxxl
 */
public final class AuditActionLabels {

    /** 动作 → 中文名；未列出的动作回落成码值本身 */
    private static final Map<AuditActionType, String> LABELS = Map.ofEntries(
            Map.entry(AuditActionType.CREATE, "创建知识库"),
            Map.entry(AuditActionType.UPDATE, "修改知识库"),
            Map.entry(AuditActionType.DISABLE, "停用知识库"),
            Map.entry(AuditActionType.ENABLE, "启用知识库"),
            Map.entry(AuditActionType.DELETE, "删除知识库"),
            Map.entry(AuditActionType.PUBLISH_INDEX, "发布索引"),
            Map.entry(AuditActionType.ROLLBACK_INDEX, "回退索引"),
            Map.entry(AuditActionType.RECYCLE_INDEX, "回收索引"),
            Map.entry(AuditActionType.RETRIEVAL_RULE_PUBLISH, "发布检索规则"),
            Map.entry(AuditActionType.USER_CREATE, "新增账号"),
            Map.entry(AuditActionType.USER_UPDATE, "修改账号"),
            Map.entry(AuditActionType.USER_DISABLE, "停用账号"),
            Map.entry(AuditActionType.USER_ENABLE, "启用账号"),
            Map.entry(AuditActionType.USER_DELETE, "删除账号"));

    /**
     * BIND 的中文名按对象类型分：绑策略与解绑都走 BIND，
     * 具体是绑定还是解绑看前后摘要（后者为空 = 解绑）。
     */
    private static final String BIND_LABEL = "绑定策略";
    private static final String UNBIND_LABEL = "解绑策略";

    private AuditActionLabels() {
    }

    /**
     * 动作中文名。
     *
     * @param action      动作类型名（库表 action_type）
     * @param afterSummary 变更后摘要；仅 BIND 用（为空表示解绑）
     * @return 中文名；未识别动作返回原码值
     */
    public static String label(String action, String afterSummary) {
        AuditActionType type = parse(action);
        if (type == null) {
            return action;
        }
        if (type == AuditActionType.BIND) {
            // 解绑时 after 为空、before 形如 "CHUNK=旧值"
            return afterSummary == null || afterSummary.isBlank() ? UNBIND_LABEL : BIND_LABEL;
        }
        return LABELS.getOrDefault(type, action);
    }

    private static AuditActionType parse(String action) {
        if (action == null || action.isBlank()) {
            return null;
        }
        for (AuditActionType type : AuditActionType.values()) {
            if (type.name().equalsIgnoreCase(action)) {
                return type;
            }
        }
        return null;
    }
}
