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
 * 乱码页降级处置：写 GARBLED_PAGE 告警 + 登记失败页，计 1 个失败单元。
 *
 * @author cxxl
 */
@Component
public class GarbledFallbackHandler implements SignalFallbackHandler {

    @Override
    public SignalSubtype subtype() {
        return SignalSubtype.GARBLED;
    }

    @Override
    public int handle(Signal signal, QualityInfo quality) {
        String region = StrUtil.blankToDefault(signal.getRegion(), "");
        quality.getWarnings().add(QualityWarning.of(QualityWarningCode.GARBLED_PAGE, null, "WARN",
                "第 " + region + " 乱码率超阈值，计失败页"));
        FallbackSupport.addFailedPage(quality, FallbackSupport.parsePage(region));
        return 1;
    }
}
