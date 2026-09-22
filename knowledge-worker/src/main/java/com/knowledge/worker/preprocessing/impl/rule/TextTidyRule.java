package com.knowledge.worker.preprocessing.impl.rule;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.common.domain.preprocess.TraceEntry;
import com.knowledge.common.domain.preprocess.ViewCell;
import com.knowledge.common.domain.preprocess.ViewElement;
import com.knowledge.common.enums.preprocess.PreprocessParam;
import com.knowledge.common.enums.preprocess.PreprocessRule;
import com.knowledge.worker.preprocessing.PreprocessProperties;
import com.knowledge.worker.preprocessing.impl.TextBase;
import com.knowledge.worker.preprocessing.rule.CleanRule;
import com.knowledge.worker.preprocessing.rule.RuleContext;
import com.knowledge.worker.preprocessing.rule.RuleOutcome;
import com.knowledge.worker.preprocessing.strategy.PreprocessStrategy;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * ② 段落/列表/表格文本整理（单设规则，拆子规则集）：文本级整理，不动结构
 * （不合并单元格/不改层级/不改行列）。子规则：空白归一化（行内空白折叠+trim+单元格空白）、
 * 标点与引号统一（重复标点折叠+弯引号转直引号）、断词连字符合并（软连字符剔除+英文断行）、
 * 项目符号后补空格、移除 URL/邮箱。无变化：表格记 KEEP，段落不记轨迹。
 *
 * @author cxxl
 */
@Component
public class TextTidyRule implements CleanRule {

    /** 弯引号（NFKC 不转换，单独统一为直引号） */
    private static final Pattern CURLY_QUOTES = Pattern.compile("[“”‘’]");

    /** URL（http/https/www 前缀，非空白字符到空白边界） */
    private static final Pattern URL_PATTERN = Pattern.compile("(?:https?://|www\\.)\\S+");

    /** 邮箱地址 */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    @Override
    public String name() {
        return "text-tidy-v1";
    }

    @Override
    public String stepName() {
        return "段落/表格文本整理";
    }

    @Override
    public int order() {
        return 2;
    }

    @Override
    public boolean enabledIn(PreprocessStrategy strategy) {
        return strategy.enabled(PreprocessRule.TIDY, true);
    }

    @Override
    public RuleOutcome apply(ViewElement element, RuleContext context) {
        PreprocessProperties properties = context.getProperties();
        PreprocessStrategy strategy = context.getStrategy();
        boolean changed = false;
        String before = element.getDisplayText();
        String display = element.getDisplayText();
        if (StrUtil.isNotBlank(display)) {
            String tidied = tidy(display, strategy);
            if (!tidied.equals(display)) {
                display = tidied;
                element.setDisplayText(tidied);
                element.setNormalizedText(TextBase.normalizeBase(tidied));
                changed = true;
            }
        }
        boolean hasCells = ObjectUtil.isNotNull(element.getCells()) && !element.getCells().isEmpty();
        if (hasCells) {
            for (ViewCell cell : element.getCells()) {
                if (StrUtil.isNotBlank(cell.getNormalizedText())) {
                    String tidied = tidy(cell.getNormalizedText(), strategy);
                    if (!tidied.equals(cell.getNormalizedText())) {
                        cell.setNormalizedText(tidied);
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            TraceEntry trace = TraceEntry.of(name(), null, TraceEntry.ACTION_REPLACE,
                    StrUtil.maxLength(before, properties.getTraceBeforeAfterMaxLen()),
                    StrUtil.maxLength(display, properties.getTraceBeforeAfterMaxLen()),
                    "文本级整理（不动结构）：空白归一化/标点与引号统一/断词连字符/项目符号间隔/URL 邮箱移除（按策略子规则）");
            return RuleOutcome.hit(trace, 1);
        }
        if (hasCells) {
            TraceEntry trace = TraceEntry.of(name(), null, TraceEntry.ACTION_KEEP,
                    null, null, "单元格文本无需改写");
            return RuleOutcome.hit(trace, 0);
        }
        return RuleOutcome.none();
    }

    private String tidy(String text, PreprocessStrategy strategy) {
        String s = text;
        // 空白归一化：行内空白折叠（多个空白→单个）+ trim
        if (strategy.boolParam(PreprocessRule.TIDY, PreprocessParam.TIDY_WHITESPACE, true)) {
            s = s.replaceAll("[ \\t\\u00A0]+", " ").trim();
        }
        // 标点与引号统一：弯引号→直引号 + 重复标点折叠
        if (strategy.boolParam(PreprocessRule.TIDY, PreprocessParam.TIDY_PUNCT, true)) {
            s = CURLY_QUOTES.matcher(s).replaceAll(match -> switch (match.group()) {
                case "“", "”" -> "\"";
                default -> "'";
            });
            s = s.replaceAll("([。！？，；：、])\\1+", "$1");
        }
        // 断词连字符合并：软连字符剔除 + 英文断行连字符（仅连字符前为 ASCII 字母且后接小写字母）
        if (strategy.boolParam(PreprocessRule.TIDY, PreprocessParam.TIDY_DASHES, true)) {
            s = s.replace("\u00AD", "");
            s = s.replaceAll("([A-Za-z])- ([a-z])", "$1-$2");
        }
        // 项目符号后补空格（•要点 → • 要点）
        if (strategy.boolParam(PreprocessRule.TIDY, PreprocessParam.TIDY_BULLETS, true)) {
            s = s.replaceAll("([•·])(\\S)", "$1 $2");
        }
        // 移除 URL/邮箱
        if (strategy.boolParam(PreprocessRule.TIDY, PreprocessParam.TIDY_URLS, true)) {
            s = URL_PATTERN.matcher(s).replaceAll("");
            s = EMAIL_PATTERN.matcher(s).replaceAll("");
            s = s.replaceAll("[ \\t\\u00A0]{2,}", " ").trim();
        }
        return s;
    }
}
