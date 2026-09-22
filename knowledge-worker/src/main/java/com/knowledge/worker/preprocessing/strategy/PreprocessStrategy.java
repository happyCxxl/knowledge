package com.knowledge.worker.preprocessing.strategy;

import cn.hutool.core.util.StrUtil;
import com.knowledge.common.enums.preprocess.PreprocessParam;
import com.knowledge.common.enums.preprocess.PreprocessRule;
import com.knowledge.worker.preprocessing.PreprocessProperties;
import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 预处理策略快照（结构化模型）：类型/名称/版本 + 七规则配置（rules）+ 自定义规则组（custom）。
 * 触发时快照进任务，执行只用快照（可复现）；解析时由 {@link PreprocessAlgorithmSpec#normalize} 补全缺省规则与默认参数。
 * 旧扁平格式（options 平铺）不再支持。
 *
 * @author cxxl
 */
@Data
public class PreprocessStrategy {

    /** 策略类型 */
    public static final String TYPE = "PREPROCESS";

    /** 内置默认策略名/版本（库内无启用版本时回退） */
    public static final String BUILTIN_NAME = "preproc-default";
    public static final String BUILTIN_VERSION = "v1";

    /** 开关取值（JSON 契约） */
    public static final String ON = "ON";
    public static final String OFF = "OFF";

    /** 策略类型（PREPROCESS） */
    private String type = TYPE;

    /** 策略名 */
    private String name;

    /** 版本号 */
    private String version;

    /** 七规则配置（键 = PreprocessRule.key()） */
    private Map<String, PreprocessRuleConfig> rules = new HashMap<>();

    /** 自定义规则组（链尾执行，见 CustomCleanRule） */
    private CustomRuleGroup custom = new CustomRuleGroup();

    /**
     * 内置默认策略：七规则全默认（页眉页脚/目录/噪声=标记、重复/字段/文本整理/编码=开、参数全默认）。
     */
    public static PreprocessStrategy defaultStrategy() {
        PreprocessStrategy strategy = new PreprocessStrategy();
        strategy.setName(BUILTIN_NAME);
        strategy.setVersion(BUILTIN_VERSION);
        return PreprocessAlgorithmSpec.normalize(strategy, new PreprocessProperties());
    }

    /** 取某规则配置（解析后必存在；缺失返回 null，调用方兜底） */
    public PreprocessRuleConfig rule(PreprocessRule rule) {
        return rules == null || rule == null ? null : rules.get(rule.key());
    }

    /** 三态规则处置方式（缺失回退默认） */
    public String action(PreprocessRule rule, String defaultValue) {
        PreprocessRuleConfig config = rule(rule);
        return config == null || StrUtil.isBlank(config.getAction()) ? defaultValue : config.getAction();
    }

    /** 开关规则取值（缺失回退默认） */
    public boolean enabled(PreprocessRule rule, boolean defaultValue) {
        PreprocessRuleConfig config = rule(rule);
        String value = config == null ? null : config.getEnabled();
        return StrUtil.isBlank(value) ? defaultValue : ON.equalsIgnoreCase(value);
    }

    /** 规则参数字符串值（缺失回退默认） */
    public String param(PreprocessRule rule, String key, String defaultValue) {
        PreprocessRuleConfig config = rule(rule);
        String value = config == null || config.getParams() == null ? null : config.getParams().get(key);
        return StrUtil.isBlank(value) ? defaultValue : value;
    }

    /** 规则数值参数（缺失/非法回退默认） */
    public int intParam(PreprocessRule rule, PreprocessParam param, int defaultValue) {
        String value = param(rule, param.key(), String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /** 规则开关参数（缺失/非法回退默认） */
    public boolean boolParam(PreprocessRule rule, PreprocessParam param, boolean defaultValue) {
        String value = param(rule, param.key(), defaultValue ? ON : OFF);
        return ON.equalsIgnoreCase(value);
    }

    /** 生效的自定义规则列表（总开关 OFF 或无规则 → 空列表） */
    public List<PreprocessCustomRule> customRules() {
        if (custom == null || !ON.equalsIgnoreCase(custom.getEnabled()) || custom.getRules() == null) {
            return List.of();
        }
        return custom.getRules();
    }

    /** 完整版本串（name-version，如 preproc-default-v1） */
    public String fullVersion() {
        return name + "-" + version;
    }
}
