package com.knowledge.worker.structure.impl.title;

import com.knowledge.worker.structure.title.TitleDecision;
import com.knowledge.worker.structure.title.TitleRule;
import com.knowledge.worker.structure.title.TitleRuleContext;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 中文括号序号标题规则：（一）…（三级；缺字号佐证只计候选）。
 *
 * @author cxxl
 */
@Component
public class CnParenTitleRule implements TitleRule {

    /** 中文序号：（一） */
    private static final Pattern CN_PAREN_PATTERN = Pattern.compile("^（[一二三四五六七八九十]+）.*");

    @Override
    public int order() {
        return 40;
    }

    @Override
    public TitleDecision tryMatch(TitleRuleContext context) {
        if (!context.shortText() || !CN_PAREN_PATTERN.matcher(context.text()).matches()) {
            return null;
        }
        return context.fontBacked()
                ? TitleDecision.title(3,
                        TitleDecision.evidence("number-pattern", context.fontSize(), context.bold(), "（一）"))
                : TitleDecision.candidate();
    }
}
