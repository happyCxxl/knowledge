package com.knowledge.worker.preprocessing.impl.rule;
import com.knowledge.common.enums.preprocess.ViewElementStatus;

import com.knowledge.common.domain.preprocess.TraceEntry;
import com.knowledge.common.domain.preprocess.ViewElement;
import com.knowledge.common.enums.preprocess.PreprocessAction;
import com.knowledge.common.enums.preprocess.PreprocessRule;
import com.knowledge.worker.preprocessing.rule.CleanRule;
import com.knowledge.worker.preprocessing.ViewElementHelper;
import com.knowledge.worker.preprocessing.strategy.PreprocessStrategy;
import com.knowledge.worker.preprocessing.rule.RuleContext;
import com.knowledge.worker.preprocessing.rule.RuleOutcome;
import org.springframework.stereotype.Component;

/**
 * ⑦ 噪声处置：判定依赖组装环节噪声页标记（NOISE_PAGE），本规则按处置方式处理
 * MARK（标记，默认）/EXCLUDE（剔除出检索文本内容流；KEEP 未实现，按 MARK 处理）。
 * 空白页无元素，仅统计与告警，无元素级处置。
 *
 * @author cxxl
 */
@Component
public class NoiseRule implements CleanRule {

    @Override
    public String name() {
        return "noise-dispose-v1";
    }

    @Override
    public String stepName() {
        return "噪声处置";
    }

    @Override
    public int order() {
        return 7;
    }

    @Override
    public boolean enabledIn(PreprocessStrategy strategy) {
        return strategy.rule(PreprocessRule.NOISE) != null;
    }

    @Override
    public RuleOutcome apply(ViewElement element, RuleContext context) {
        if (element.getPage() == null || !context.getNoisePageNumbers().contains(element.getPage())) {
            return RuleOutcome.none();
        }
        String opt = context.getStrategy().action(PreprocessRule.NOISE, PreprocessAction.MARK.name());
        TraceEntry trace;
        int changed = 0;
        if (PreprocessAction.EXCLUDE.name().equalsIgnoreCase(opt)) {
            element.setStatus(ViewElementStatus.EXCLUDED_NOISE.name());
            element.setNormalizedText(null);
            ViewElementHelper.clearCellTexts(element);
            trace = TraceEntry.of(name(), null, TraceEntry.ACTION_EXCLUDE, null, null,
                    "策略 noise=EXCLUDE，噪声页元素剔除出检索文本");
            changed = 1;
        } else {
            element.setStatus(ViewElementStatus.NOISE.name());
            trace = TraceEntry.of(name(), null, TraceEntry.ACTION_MARK, null, null,
                    "组装环节噪声页标记，处置=标记（仍参与内容流）");
        }
        return RuleOutcome.hit(trace, changed);
    }
}
