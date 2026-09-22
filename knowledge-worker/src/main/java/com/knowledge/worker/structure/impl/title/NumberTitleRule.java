package com.knowledge.worker.structure.impl.title;

import com.knowledge.worker.structure.title.TitleDecision;
import com.knowledge.worker.structure.title.TitleRule;
import com.knowledge.worker.structure.title.TitleRuleContext;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多级数字编号标题规则：1.1 / 1.1.1 …（点数定级）；
 * 防误判：含 CJK + 字号佐证才定级，否则只计候选告警。
 *
 * @author cxxl
 */
@Component
public class NumberTitleRule implements TitleRule {

    /** 多级数字：1.1 / 1.1.1 …（点数定级） */
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^(\\d+(?:\\.\\d+)+).*");

    @Override
    public int order() {
        return 30;
    }

    @Override
    public TitleDecision tryMatch(TitleRuleContext context) {
        if (!context.shortText()) {
            return null;
        }
        Matcher number = NUMBER_PATTERN.matcher(context.text());
        if (!number.matches()) {
            return null;
        }
        int level = countDots(number.group(1)) + 1;
        boolean hasCjk = context.text().codePoints().anyMatch(cp -> cp >= 0x4E00 && cp <= 0x9FFF);
        if (hasCjk && context.fontBacked()) {
            return TitleDecision.title(level,
                    TitleDecision.evidence("number-pattern", context.fontSize(), context.bold(), "1.1"));
        }
        return TitleDecision.candidate();
    }

    private int countDots(String number) {
        int dots = 0;
        for (char c : number.toCharArray()) {
            if (c == '.') {
                dots++;
            }
        }
        return dots;
    }
}
