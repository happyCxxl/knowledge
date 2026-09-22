package com.knowledge.common.enums.preprocess;

import cn.hutool.core.util.StrUtil;

/**
 * 自定义规则动作：用户自定义正则规则的三种动作。
 *
 * @author cxxl
 */
public enum CustomRuleAction {

    /** 命中整体移除 */
    REMOVE,

    /** 命中部分替换（replacement 支持 $1 组引用，必填） */
    REPLACE,

    /** 仅保留命中内容（替换原文本） */
    EXTRACT;

    /** 按名称解析（大小写不敏感）；未识别返回 null */
    public static CustomRuleAction of(String name) {
        if (StrUtil.isBlank(name)) {
            return null;
        }
        for (CustomRuleAction action : values()) {
            if (action.name().equalsIgnoreCase(name)) {
                return action;
            }
        }
        return null;
    }
}
