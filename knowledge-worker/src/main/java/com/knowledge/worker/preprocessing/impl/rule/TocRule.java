package com.knowledge.worker.preprocessing.impl.rule;
import com.knowledge.common.enums.structure.ElementMark;
import com.knowledge.common.enums.preprocess.ViewElementStatus;

import com.knowledge.common.domain.preprocess.TraceEntry;
import com.knowledge.common.domain.preprocess.ViewElement;
import com.knowledge.common.enums.preprocess.PreprocessAction;
import com.knowledge.common.enums.preprocess.PreprocessParam;
import com.knowledge.common.enums.preprocess.PreprocessRule;
import com.knowledge.worker.preprocessing.rule.CleanRule;
import com.knowledge.worker.preprocessing.strategy.PreprocessStrategy;
import com.knowledge.worker.preprocessing.rule.RuleContext;
import com.knowledge.worker.preprocessing.rule.RuleOutcome;
import org.springframework.stereotype.Component;

/**
 * ④ 目录处置：识别在解析环节（TOC_LINE 行级特征），本规则做"整页判目录页"聚合
 * （PDF：该页 TOC_LINE 元素数 ≥ tocMinLinesPerPage；Word：连续窗口）后按处置方式处理。
 * 非目录页的特征行不受影响（防误删）。
 *
 * @author cxxl
 */
@Component
public class TocRule implements CleanRule {

    @Override
    public String name() {
        return "toc-dispose-v1";
    }

    @Override
    public String stepName() {
        return "目录处置";
    }

    @Override
    public int order() {
        return 4;
    }

    @Override
    public boolean enabledIn(PreprocessStrategy strategy) {
        return strategy.rule(PreprocessRule.TOC) != null;
    }

    @Override
    public RuleOutcome apply(ViewElement element, RuleContext context) {
        if (element.getMarks() == null || !element.getMarks().contains(ElementMark.TOC_LINE.name())) {
            return RuleOutcome.none();
        }
        boolean tocPage;
        String evidence;
        if (element.getPage() != null) {
            int count = context.getTocLineCountByPage().getOrDefault(element.getPage(), 0);
            int minLinesPerPage = context.getStrategy()
                    .intParam(PreprocessRule.TOC, PreprocessParam.TOC_MIN_LINES_PER_PAGE,
                            context.getProperties().getTocMinLinesPerPage());
            tocPage = count >= minLinesPerPage;
            evidence = "整页判为目录页（该页 TOC_LINE " + count + " 行，阈值 " + minLinesPerPage + "）";
        } else {
            tocPage = context.getTocRunElementIds().contains(element.getElementId());
            evidence = "连续 TOC_LINE 窗口（Word 无页概念）";
        }
        if (!tocPage) {
            return RuleOutcome.none();
        }
        String opt = context.getStrategy().action(PreprocessRule.TOC, PreprocessAction.MARK.name());
        TraceEntry trace;
        int changed = 0;
        if (PreprocessAction.EXCLUDE.name().equalsIgnoreCase(opt)) {
            element.setStatus(ViewElementStatus.EXCLUDED_TOC.name());
            element.setNormalizedText(null);
            trace = TraceEntry.of(name(), null, TraceEntry.ACTION_EXCLUDE, null, null,
                    "策略 toc=EXCLUDE，剔除出检索文本；" + evidence);
            changed = 1;
        } else if (PreprocessAction.KEEP.name().equalsIgnoreCase(opt)) {
            element.setStatus(ViewElementStatus.NORMAL.name());
            trace = TraceEntry.of(name(), null, TraceEntry.ACTION_KEEP, null, null,
                    "策略 toc=KEEP，保留原样；" + evidence);
        } else {
            element.setStatus(ViewElementStatus.MARKED_TOC.name());
            trace = TraceEntry.of(name(), null, TraceEntry.ACTION_MARK, null, null, evidence);
        }
        return RuleOutcome.hit(trace, changed);
    }
}
