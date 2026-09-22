package com.knowledge.worker.preprocessing.impl.rule;
import com.knowledge.common.enums.structure.ElementMark;
import com.knowledge.common.enums.preprocess.ViewElementStatus;

import com.knowledge.common.domain.preprocess.TraceEntry;
import com.knowledge.common.domain.preprocess.ViewElement;
import com.knowledge.common.enums.preprocess.PreprocessRule;
import com.knowledge.worker.preprocessing.rule.CleanRule;
import com.knowledge.worker.preprocessing.ViewElementHelper;
import com.knowledge.worker.preprocessing.strategy.PreprocessStrategy;
import com.knowledge.worker.preprocessing.rule.RuleContext;
import com.knowledge.worker.preprocessing.rule.RuleOutcome;
import org.springframework.stereotype.Component;

/**
 * ⑤ 重复处置：判定依赖组装环节标记（REPEATED_SEGMENT 元素 / REPEATED_PAGE 页，除首份外），
 * 本规则把重复份剔除出检索文本内容流（只保留首份），展示视图完整。
 *
 * @author cxxl
 */
@Component
public class RepeatRule implements CleanRule {

    @Override
    public String name() {
        return "repeat-dispose-v1";
    }

    @Override
    public String stepName() {
        return "重复处置";
    }

    @Override
    public int order() {
        return 5;
    }

    @Override
    public boolean enabledIn(PreprocessStrategy strategy) {
        return strategy.enabled(PreprocessRule.REPEAT, true);
    }

    @Override
    public RuleOutcome apply(ViewElement element, RuleContext context) {
        boolean repeatedSegment = element.getMarks() != null
                && element.getMarks().contains(ElementMark.REPEATED_SEGMENT.name());
        boolean repeatedPage = element.getPage() != null
                && context.getRepeatedPageNumbers().contains(element.getPage());
        if (!repeatedSegment && !repeatedPage) {
            return RuleOutcome.none();
        }
        element.setStatus(ViewElementStatus.REPEATED.name());
        element.setNormalizedText(null);
        ViewElementHelper.clearCellTexts(element);
        TraceEntry trace = TraceEntry.of(name(), null, TraceEntry.ACTION_EXCLUDE, null, null,
                repeatedPage ? "组装环节重复页标记：检索文本只保留一份（首份保留）"
                        : "组装环节重复段标记：检索文本只保留一份（首份保留）");
        return RuleOutcome.hit(trace, 1);
    }
}
