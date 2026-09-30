package com.knowledge.common.utils;

import cn.hutool.core.util.ObjectUtil;
import com.knowledge.common.security.KnowledgeUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具。
 *
 * @author cxxl
 */
public final class SecurityUtil {

    private SecurityUtil() {
    }

    /**
     * 取当前登录用户；无认证上下文返回 null
     */
    public static KnowledgeUser getUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (ObjectUtil.isNotNull(authentication) && authentication.getPrincipal() instanceof KnowledgeUser) {
            return (KnowledgeUser) authentication.getPrincipal();
        }
        return null;
    }
}
