package com.knowledge.worker.embedding.strategy;

import cn.hutool.core.util.StrUtil;
import com.knowledge.common.enums.embed.EmbeddingModel;
import com.knowledge.model.catalog.ModelCatalogPort;
import com.knowledge.worker.embedding.EmbedProperties;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 向量化策略规格：默认补齐（normalize）/ 保存校验（validate）/ 保存时目录冗余（enrich）。
 * 模型目录（维度/度量/归一化/窗口/批上限）是单一事实源，目录冗余字段一律以目录为准回填。
 * biz 保存校验与前端表单共用本口径。
 *
 * @author cxxl
 */
public final class EmbedAlgorithmSpec {

    private EmbedAlgorithmSpec() {
    }

    /** docTemplate 允许的占位符 */
    private static final Set<String> DOC_PLACEHOLDERS = Set.of("{content}", "{titlePath}", "{contentType}", "{tableRef}");

    /** queryTemplate 允许的占位符 */
    private static final Set<String> QUERY_PLACEHOLDERS = Set.of("{query}");

    // ---------------- 默认补齐（解析时） ----------------

    /**
     * 解析后补全：模型缺失回退首个启用模型；模板/批量/执行口径补默认；目录冗余字段一律以目录为准回填。
     */
    public static EmbedStrategy normalize(EmbedStrategy strategy, EmbedProperties properties, ModelCatalogPort catalog) {
        if (StrUtil.isBlank(strategy.getModel())) {
            strategy.setModel(EmbeddingModel.firstEnabled().key());
        }
        if (StrUtil.isBlank(strategy.getDocTemplate())) {
            strategy.setDocTemplate("{content}");
        }
        if (StrUtil.isBlank(strategy.getQueryTemplate())) {
            strategy.setQueryTemplate("{query}");
        }
        if (strategy.getBatchSize() == null) {
            strategy.setBatchSize(properties.getBatchSize());
        }
        if (strategy.getTimeoutMs() == null) {
            strategy.setTimeoutMs(properties.getTimeoutMs());
        }
        if (strategy.getMaxRetries() == null) {
            strategy.setMaxRetries(properties.getMaxRetries());
        }
        strategy.setCacheEnabled(enumOr(strategy.getCacheEnabled(), properties.getCacheEnabled()));
        strategy.setIncludeParent(enumOr(strategy.getIncludeParent(), properties.getIncludeParent()));
        strategy.setSkipEmpty(enumOr(strategy.getSkipEmpty(), properties.getSkipEmpty()));
        stampCatalog(strategy, catalog);
        return strategy;
    }

    /** 开关值规范：空回退默认，非空统一大写 */
    private static String enumOr(String value, String defaultValue) {
        if (StrUtil.isBlank(value)) {
            return defaultValue;
        }
        return value.trim().toUpperCase();
    }

    /** 目录冗余回填：以模型目录为准覆盖 dimension/metric/normalized/contextWindowTokens/batchLimit */
    private static void stampCatalog(EmbedStrategy strategy, ModelCatalogPort catalog) {
        EmbeddingModel model = catalog == null ? null : catalog.get(strategy.getModel());
        if (model == null) {
            return;
        }
        strategy.setDimension(model.dimension());
        strategy.setMetric(model.metric().key());
        strategy.setNormalized(model.normalized());
        strategy.setContextWindowTokens(model.contextWindowTokens());
        strategy.setBatchLimit(model.batchLimit());
    }

    // ---------------- 保存校验（biz 调用；返回 null = 通过，否则为错误信息） ----------------

    /** 校验配置 JSON；非法返回错误信息，合法返回 null。config 应为模型/模板/批量/执行口径结构（无 type/name/version 顶层键）。 */
    public static String validate(Map<String, Object> config, ModelCatalogPort catalog) {
        if (config == null) {
            return "配置不能为空";
        }
        String model = String.valueOf(config.getOrDefault("model", ""));
        if (StrUtil.isBlank(model)) {
            return "模型不能为空";
        }
        EmbeddingModel catalogModel = catalog == null ? null : catalog.get(model);
        if (catalogModel == null) {
            return "模型不存在: " + model;
        }
        if (!catalogModel.enabled()) {
            return "模型未启用: " + model;
        }
        String templateError = validateTemplate(config.get("docTemplate"), "{content}", DOC_PLACEHOLDERS, "docTemplate");
        if (templateError != null) {
            return templateError;
        }
        templateError = validateTemplate(config.get("queryTemplate"), "{query}", QUERY_PLACEHOLDERS, "queryTemplate");
        if (templateError != null) {
            return templateError;
        }
        String rangeError = validateRange(config, "batchSize", 1, 128);
        if (rangeError != null) {
            return rangeError;
        }
        rangeError = validateRange(config, "timeoutMs", 1000, 120000);
        if (rangeError != null) {
            return rangeError;
        }
        rangeError = validateRange(config, "maxRetries", 0, 5);
        if (rangeError != null) {
            return rangeError;
        }
        for (String key : new String[] { "cacheEnabled", "includeParent", "skipEmpty" }) {
            String enumError = validateEnum(config, key);
            if (enumError != null) {
                return enumError;
            }
        }
        return null; // 未知键透传不报错（前向兼容）
    }

    /** 模板校验：非空时必含 required 占位符且全部占位符在白名单内 */
    private static String validateTemplate(Object value, String required, Set<String> allowed, String field) {
        if (value == null || StrUtil.isBlank(String.valueOf(value))) {
            return null; // 空走默认，不校验
        }
        String template = String.valueOf(value);
        if (!template.contains(required)) {
            return field + " 必须包含 " + required + " 占位符";
        }
        for (String placeholder : extractPlaceholders(template)) {
            if (!allowed.contains(placeholder)) {
                return field + " 占位符不在白名单: " + placeholder;
            }
        }
        return null;
    }

    /** 提取 {xxx} 占位符（去重） */
    private static Set<String> extractPlaceholders(String template) {
        Set<String> placeholders = new LinkedHashSet<>();
        int from = 0;
        while (true) {
            int open = template.indexOf('{', from);
            if (open < 0) {
                break;
            }
            int close = template.indexOf('}', open);
            if (close < 0) {
                break;
            }
            placeholders.add(template.substring(open, close + 1));
            from = close + 1;
        }
        return placeholders;
    }

    private static String validateRange(Map<String, Object> config, String key, int min, int max) {
        Object value = config.get(key);
        if (value == null) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(value).trim());
            if (parsed < min || parsed > max) {
                return key + " 越界（允许 " + min + "~" + max + "）";
            }
            return null;
        } catch (NumberFormatException e) {
            return key + " 必须是整数";
        }
    }

    private static String validateEnum(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (!EmbedStrategy.ON.equalsIgnoreCase(text) && !EmbedStrategy.OFF.equalsIgnoreCase(text)) {
            return key + " 只能是 ON/OFF";
        }
        return null;
    }

    // ---------------- 保存时目录冗余（biz 保存路径调用） ----------------

    /**
     * 校验通过后回填目录冗余进快照（dimension/metric/normalized/contextWindowTokens/batchLimit），
     * 返回新的配置 Map（不改入参）；校验失败返回 null 且调用方以 validate 错误信息提示。
     */
    public static Map<String, Object> enrich(Map<String, Object> config, ModelCatalogPort catalog) {
        if (validate(config, catalog) != null) {
            return null;
        }
        Map<String, Object> stamped = new HashMap<>(config);
        EmbeddingModel model = catalog.get(String.valueOf(config.get("model")));
        stamped.put("dimension", model.dimension());
        stamped.put("metric", model.metric().key());
        stamped.put("normalized", model.normalized());
        stamped.put("contextWindowTokens", model.contextWindowTokens());
        stamped.put("batchLimit", model.batchLimit());
        return stamped;
    }
}
