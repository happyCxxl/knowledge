package com.knowledge.common.domain.rules;

import cn.hutool.core.util.ObjectUtil;
import com.knowledge.common.domain.entity.KnowledgeBase;
import com.knowledge.common.enums.knowledge.KnowledgeBaseStatus;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;

/**
 * 知识库状态规则：status 两态（启用/停用）迁移校验 + 默认库保护。
 *
 * @author cxxl
 */
public final class KnowledgeBaseRules {

    private KnowledgeBaseRules() {
    }

    /**
     * 停用校验：仅启用状态（ACTIVE=1）可停用。
     *
     * @param kb 知识库实体
     * @throws KnowledgeException 非启用状态（KB_STATUS_ILLEGAL 40402）
     */
    public static void checkCanDisable(KnowledgeBase kb) {
        requireStatus(kb, KnowledgeBaseStatus.ACTIVE, ErrorCode.KB_STATUS_ILLEGAL, "仅启用状态的知识库可以停用");
    }

    /**
     * 启用校验：仅停用状态（DISABLED=0）可启用。
     *
     * @param kb 知识库实体
     * @throws KnowledgeException 非停用状态（KB_STATUS_ILLEGAL 40402）
     */
    public static void checkCanEnable(KnowledgeBase kb) {
        requireStatus(kb, KnowledgeBaseStatus.DISABLED, ErrorCode.KB_STATUS_ILLEGAL, "仅停用状态的知识库可以启用");
    }

    /**
     * 默认知识库保护校验：默认库（default_flag=1）不可停用/删除。
     * 启用不拦截，允许把异常置停的默认库修回启用态。
     *
     * @param kb 知识库实体
     * @throws KnowledgeException 默认库（KB_STATUS_ILLEGAL 40402）
     */
    public static void checkNotDefault(KnowledgeBase kb) {
        if (ObjectUtil.isNotNull(kb) && Integer.valueOf(1).equals(kb.getDefaultFlag())) {
            throw new KnowledgeException(ErrorCode.KB_STATUS_ILLEGAL, "默认知识库不可停用或删除");
        }
    }

    /**
     * 提交校验：仅启用状态的知识库可接收文档提交。
     *
     * @param kb 知识库实体
     * @throws KnowledgeException 非启用状态（KB_NOT_ACTIVE 40421）
     */
    public static void checkCanSubmit(KnowledgeBase kb) {
        requireStatus(kb, KnowledgeBaseStatus.ACTIVE, ErrorCode.KB_NOT_ACTIVE, null);
    }

    /** 状态闸门公共实现：状态缺失或非预期即抛对应错误码 */
    private static void requireStatus(KnowledgeBase kb, KnowledgeBaseStatus expected, ErrorCode errorCode,
                                      String message) {
        if (ObjectUtil.isNull(kb.getStatus()) || !ObjectUtil.equal(kb.getStatus(), expected.getCode())) {
            if (message == null) {
                throw new KnowledgeException(errorCode);
            }
            throw new KnowledgeException(errorCode, message);
        }
    }

    /**
     * 策略绑定开关判定：null 或非 0 视为开启（兼容存量数据）。
     */
    public static boolean isStrategyBindingEnabled(KnowledgeBase kb) {
        return kb == null || !Integer.valueOf(0).equals(kb.getStrategyBindingEnabled());
    }
}
