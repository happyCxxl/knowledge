package com.knowledge.worker.preprocessing.impl.rule;
import com.knowledge.common.enums.structure.UnifiedElementType;
import com.knowledge.common.enums.preprocess.ViewElementStatus;

import com.knowledge.common.domain.preprocess.TraceEntry;
import com.knowledge.common.domain.preprocess.ViewElement;
import com.knowledge.common.enums.preprocess.PreprocessAction;
import com.knowledge.common.enums.preprocess.PreprocessRule;
import com.knowledge.worker.preprocessing.rule.CleanRule;
import com.knowledge.worker.preprocessing.strategy.PreprocessStrategy;
import com.knowledge.worker.preprocessing.rule.RuleContext;
import com.knowledge.worker.preprocessing.rule.RuleOutcome;
import org.springframework.stereotype.Component;

/**
 * ③ 页眉页脚处置：识别在解析环节（HEADER/FOOTER 元素），本规则按处置方式处理
 * KEEP（保留）/MARK（标记，默认）/EXCLUDE（剔除出检索文本内容流，展示视图完整）。
 *
 * @author cxxl
 */
@Component
public class HeaderFooterRule implements CleanRule {

    @Override
    public String name() {
        return "header-footer-dispose-v1";
    }

    @Override
    public String stepName() {
        return "页眉页脚处置";
    }

    @Override
    public int order() {
        return 3;
    }

    @Override
    public boolean enabledIn(PreprocessStrategy strategy) {
        return strategy.rule(PreprocessRule.HEADER_FOOTER) != null;
    }

    @Override
    public RuleOutcome apply(ViewElement element, RuleContext context) {
        boolean header = UnifiedElementType.HEADER.name().equals(element.getType());
        boolean footer = UnifiedElementType.FOOTER.name().equals(element.getType());
        if (!header && !footer) {
            return RuleOutcome.none();
        }
        String opt = context.getStrategy().action(PreprocessRule.HEADER_FOOTER, PreprocessAction.MARK.name());
        TraceEntry trace;
        int changed = 0;
        if (PreprocessAction.EXCLUDE.name().equalsIgnoreCase(opt)) {
            element.setStatus(header ? ViewElementStatus.EXCLUDED_HEADER.name() : ViewElementStatus.EXCLUDED_FOOTER.name());
            element.setNormalizedText(null);
            trace = TraceEntry.of(name(), null, TraceEntry.ACTION_EXCLUDE, null, null,
                    "策略 headerFooter=EXCLUDE，剔除出检索文本");
            changed = 1;
        } else if (PreprocessAction.KEEP.name().equalsIgnoreCase(opt)) {
            element.setStatus(ViewElementStatus.NORMAL.name());
            trace = TraceEntry.of(name(), null, TraceEntry.ACTION_KEEP, null, null,
                    "策略 headerFooter=KEEP，保留原样");
        } else {
            element.setStatus(header ? ViewElementStatus.MARKED_HEADER.name() : ViewElementStatus.MARKED_FOOTER.name());
            trace = TraceEntry.of(name(), null, TraceEntry.ACTION_MARK, null, null,
                    "解析环节识别的页眉/页脚元素，处置=标记（仍参与内容流）");
        }
        return RuleOutcome.hit(trace, changed);
    }
}
