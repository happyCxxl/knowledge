package com.knowledge.worker.parser.impl.fallback;

import cn.hutool.core.util.StrUtil;
import com.knowledge.common.domain.parse.QualityInfo;
import com.knowledge.common.domain.parse.QualityWarning;
import com.knowledge.common.domain.parse.signal.Signal;
import com.knowledge.common.enums.parse.QualityWarningCode;
import com.knowledge.common.enums.parse.SignalSubtype;
import com.knowledge.worker.parser.signal.SignalFallbackHandler;
import org.springframework.stereotype.Component;

/**
 * 表格规则失败降级处置：区域降级为段落（写告警，不计失败单元）。
 *
 * @author cxxl
 */
@Component
public class TableRuleFailedFallbackHandler implements SignalFallbackHandler {

    @Override
    public SignalSubtype subtype() {
        return SignalSubtype.TABLE_RULE_FAILED;
    }

    @Override
    public int handle(Signal signal, QualityInfo quality) {
        String region = StrUtil.blankToDefault(signal.getRegion(), "");
        quality.getWarnings().add(QualityWarning.of(QualityWarningCode.TABLE_RULE_FALLBACK, null, "WARN",
                region + " 表格规则失败，区域降级为段落"));
        return 0;
    }
}
