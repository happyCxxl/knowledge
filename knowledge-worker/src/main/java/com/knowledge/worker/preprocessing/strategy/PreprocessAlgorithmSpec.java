package com.knowledge.worker.preprocessing.strategy;

import cn.hutool.core.util.StrUtil;
import com.knowledge.common.enums.preprocess.CustomRuleAction;
import com.knowledge.common.enums.preprocess.PreprocessAction;
import com.knowledge.common.enums.preprocess.PreprocessParam;
import com.knowledge.common.enums.preprocess.PreprocessRule;
import com.knowledge.worker.preprocessing.PreprocessProperties;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 预处理算法规格：默认补齐 / 保存校验（biz 调用）。
 * 规则目录与参数目录在 common.enums.preprocess（单一事实源）；前端表单范围与置灰口径一致。
 *
 * @author cxxl
 */
public final class PreprocessAlgorithmSpec {

    /** 自定义规则上限（防滥用/正则灾难回溯） */
    public static final int CUSTOM_RULE_MAX = 20;

    /** 单条正则最大长度 */
    public static final int CUSTOM_PATTERN_MAX_LEN = 200;

    private PreprocessAlgorithmSpec() {
    }

    // ---------------- 默认补齐 ----------------

    /** 三态规则默认处置方式（开关规则返回 null） */
    public static String defaultAction(PreprocessRule rule) {
        return rule.triState() ? PreprocessAction.MARK.name() : null;
    }

    /** 开关规则默认值（三态规则返回 false） */
    public static boolean defaultEnabled(PreprocessRule rule) {
        return switch (rule) {
            case REPEAT, FIELD, TIDY, ENCODING -> true;
            default -> false;
        };
    }

    /** 各规则默认参数（值源 = PreprocessProperties 全局默认，Nacos 可调） */
    public static Map<String, String> defaultParams(PreprocessRule rule, PreprocessProperties properties) {
        Map<String, String> defaults = new LinkedHashMap<>();
        switch (rule) {
            case TOC -> {
                defaults.put(PreprocessParam.TOC_MIN_LINES_PER_PAGE.key(),
                        String.valueOf(properties.getTocMinLinesPerPage()));
                defaults.put(PreprocessParam.TOC_RUN_MIN_LENGTH.key(),
                        String.valueOf(properties.getTocRunMinLength()));
            }
            case FIELD -> {
                defaults.put(PreprocessParam.FIELD_AMOUNT.key(), PreprocessStrategy.ON);
                defaults.put(PreprocessParam.FIELD_DATE.key(), PreprocessStrategy.ON);
                defaults.put(PreprocessParam.FIELD_AREA.key(), PreprocessStrategy.ON);
                defaults.put(PreprocessParam.FIELD_CERT_NO.key(), PreprocessStrategy.ON);
            }
            case TIDY -> {
                defaults.put(PreprocessParam.TIDY_WHITESPACE.key(), PreprocessStrategy.ON);
                defaults.put(PreprocessParam.TIDY_PUNCT.key(), PreprocessStrategy.ON);
                defaults.put(PreprocessParam.TIDY_DASHES.key(), PreprocessStrategy.ON);
                defaults.put(PreprocessParam.TIDY_BULLETS.key(), PreprocessStrategy.ON);
                defaults.put(PreprocessParam.TIDY_URLS.key(), PreprocessStrategy.ON);
            }
            default -> {
            }
        }
        return defaults;
    }

    /**
     * 解析后补全：缺规则补默认（action/enabled）、缺参数补全局默认、custom 补默认。
     */
    public static PreprocessStrategy normalize(PreprocessStrategy strategy, PreprocessProperties properties) {
        if (strategy.getRules() == null) {
            strategy.setRules(new HashMap<>());
        }
        for (PreprocessRule rule : PreprocessRule.values()) {
            PreprocessRuleConfig config = strategy.getRules().get(rule.key());
            if (config == null) {
                config = new PreprocessRuleConfig();
                strategy.getRules().put(rule.key(), config);
            }
            if (config.getParams() == null) {
                config.setParams(new HashMap<>());
            }
            if (StrUtil.isBlank(config.getAction()) && defaultAction(rule) != null) {
                config.setAction(defaultAction(rule));
            }
            if (StrUtil.isBlank(config.getEnabled()) && defaultEnabled(rule)) {
                config.setEnabled(PreprocessStrategy.ON);
            }
            defaultParams(rule, properties).forEach(config.getParams()::putIfAbsent);
        }
        if (strategy.getCustom() == null) {
            strategy.setCustom(new CustomRuleGroup());
        } else {
            if (StrUtil.isBlank(strategy.getCustom().getEnabled())) {
                strategy.getCustom().setEnabled(PreprocessStrategy.ON);
            }
            if (strategy.getCustom().getRules() == null) {
                strategy.getCustom().setRules(new java.util.ArrayList<>());
            }
        }
        return strategy;
    }

    // ---------------- 保存校验（biz 调用；返回 null = 通过，否则为错误信息） ----------------

    /** 校验配置 JSON 结构；非法返回错误信息，合法返回 null。configSnapshot 应为 {"rules":{...},"custom":{...}} 结构。 */
    @SuppressWarnings("unchecked")
    public static String validate(Map<String, Object> config) {
        if (config == null) {
            return "配置不能为空";
        }
        Object rulesObj = config.get("rules");
        if (rulesObj != null && !(rulesObj instanceof Map)) {
            return "rules 必须是对象";
        }
        Map<String, Object> rules = (Map<String, Object>) (rulesObj == null ? Map.of() : rulesObj);
        for (Map.Entry<String, Object> entry : rules.entrySet()) {
            PreprocessRule rule = PreprocessRule.of(entry.getKey());
            if (rule == null) {
                continue; // 未知规则键透传不报错（前向兼容）
            }
            Object ruleValue = entry.getValue();
            if (ruleValue != null && !(ruleValue instanceof Map)) {
                return "rules." + entry.getKey() + " 必须是对象";
            }
            Map<String, Object> ruleMap = (Map<String, Object>) (ruleValue == null ? Map.of() : ruleValue);
            String ruleError = validateRuleConfig(rule, ruleMap);
            if (ruleError != null) {
                return ruleError;
            }
        }
        return validateCustom(config.get("custom"));
    }

    private static String validateRuleConfig(PreprocessRule rule, Map<String, Object> ruleMap) {
        Object actionObj = ruleMap.get("action");
        if (actionObj != null) {
            String action = String.valueOf(actionObj);
            if (!isAction(action)) {
                return "处置方式非法: " + rule.key() + "." + action;
            }
            if (!rule.triState()) {
                return "规则 " + rule.key() + " 无处置方式，请使用 enabled 开关";
            }
        }
        Object enabledObj = ruleMap.get("enabled");
        if (enabledObj != null) {
            String enabled = String.valueOf(enabledObj);
            if (!PreprocessStrategy.ON.equalsIgnoreCase(enabled)
                    && !PreprocessStrategy.OFF.equalsIgnoreCase(enabled)) {
                return "开关取值非法: " + rule.key() + "." + enabled;
            }
            if (rule.triState()) {
                return "规则 " + rule.key() + " 无开关，请使用 action 处置方式";
            }
        }
        Object paramsObj = ruleMap.get("params");
        if (paramsObj != null && !(paramsObj instanceof Map)) {
            return "rules." + rule.key() + ".params 必须是对象";
        }
        Map<String, Object> params = (Map<String, Object>) (paramsObj == null ? Map.of() : paramsObj);
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            PreprocessParam param = PreprocessParam.of(rule, entry.getKey());
            if (param == null) {
                continue; // 未知参数键透传不报错（前向兼容）
            }
            String value = String.valueOf(entry.getValue());
            if (param.numeric()) {
                try {
                    int parsed = Integer.parseInt(value.trim());
                    if (parsed < param.min() || parsed > param.max()) {
                        return "参数越界: " + rule.key() + "." + param.key()
                                + "（允许 " + param.min() + "~" + param.max() + "）";
                    }
                } catch (NumberFormatException e) {
                    return "参数必须是整数: " + rule.key() + "." + param.key();
                }
            } else if (!PreprocessStrategy.ON.equalsIgnoreCase(value)
                    && !PreprocessStrategy.OFF.equalsIgnoreCase(value)) {
                return "参数取值非法: " + rule.key() + "." + param.key() + "（允许 ON/OFF）";
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static String validateCustom(Object customObj) {
        if (customObj == null) {
            return null;
        }
        if (!(customObj instanceof Map)) {
            return "custom 必须是对象";
        }
        Map<String, Object> custom = (Map<String, Object>) customObj;
        Object enabledObj = custom.get("enabled");
        if (enabledObj != null) {
            String enabled = String.valueOf(enabledObj);
            if (!PreprocessStrategy.ON.equalsIgnoreCase(enabled)
                    && !PreprocessStrategy.OFF.equalsIgnoreCase(enabled)) {
                return "custom.enabled 取值非法（允许 ON/OFF）";
            }
        }
        Object rulesObj = custom.get("rules");
        if (rulesObj == null) {
            return null;
        }
        if (!(rulesObj instanceof List<?> customRules)) {
            return "custom.rules 必须是数组";
        }
        if (customRules.size() > CUSTOM_RULE_MAX) {
            return "自定义规则最多 " + CUSTOM_RULE_MAX + " 条";
        }
        for (Object item : customRules) {
            if (!(item instanceof Map)) {
                return "自定义规则必须是对象";
            }
            Map<String, Object> ruleMap = (Map<String, Object>) item;
            String pattern = String.valueOf(ruleMap.getOrDefault("pattern", ""));
            if (pattern.isBlank()) {
                return "自定义规则的正则表达式不能为空";
            }
            if (pattern.length() > CUSTOM_PATTERN_MAX_LEN) {
                return "自定义规则的正则表达式过长（≤" + CUSTOM_PATTERN_MAX_LEN + " 字符）";
            }
            try {
                Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                return "自定义规则存在不合法的正则表达式: " + pattern;
            }
            String action = String.valueOf(ruleMap.getOrDefault("action", ""));
            if (CustomRuleAction.of(action) == null) {
                return "自定义规则动作非法: " + action + "（允许 REMOVE/REPLACE/EXTRACT）";
            }
            Object replacement = ruleMap.get("replacement");
            if (CustomRuleAction.REPLACE.name().equalsIgnoreCase(action)
                    && (replacement == null || String.valueOf(replacement).isEmpty())) {
                return "替换动作需填写替换文本";
            }
            if (!CustomRuleAction.REPLACE.name().equalsIgnoreCase(action) && replacement != null) {
                return action + " 动作不允许携带替换文本";
            }
        }
        return null;
    }

    private static boolean isAction(String value) {
        for (PreprocessAction action : PreprocessAction.values()) {
            if (action.name().equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }
}
