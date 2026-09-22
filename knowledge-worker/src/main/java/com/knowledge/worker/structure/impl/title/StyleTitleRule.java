package com.knowledge.worker.structure.impl.title;

import cn.hutool.core.util.StrUtil;
import com.knowledge.worker.structure.title.TitleDecision;
import com.knowledge.worker.structure.title.TitleRule;
import com.knowledge.worker.structure.title.TitleRuleContext;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 原生样式标题规则（级联第一层）：DOCX Heading1~9 直接定级（无深度上限，不要求短句）。
 *
 * @author cxxl
 */
@Component
public class StyleTitleRule implements TitleRule {

    /** DOCX 样式标题：Heading1~9 */
    private static final Pattern STYLE_PATTERN = Pattern.compile("Heading(\\d+)", Pattern.CASE_INSENSITIVE);

    @Override
    public int order() {
        return 10;
    }

    @Override
    public TitleDecision tryMatch(TitleRuleContext context) {
        String style = context.style();
        if (StrUtil.isBlank(style)) {
            return null;
        }
        Matcher matcher = STYLE_PATTERN.matcher(style);
        if (!matcher.find()) {
            return null;
        }
        return TitleDecision.title(Integer.parseInt(matcher.group(1)),
                TitleDecision.evidence("style", context.fontSize(), context.bold(), style));
    }
}
