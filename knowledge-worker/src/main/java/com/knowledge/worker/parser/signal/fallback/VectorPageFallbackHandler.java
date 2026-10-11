package com.knowledge.worker.parser.signal.fallback;

import cn.hutool.core.util.StrUtil;
import com.knowledge.common.domain.parse.QualityInfo;
import com.knowledge.common.domain.parse.QualityWarning;
import com.knowledge.common.domain.parse.signal.Signal;
import com.knowledge.common.enums.parse.QualityWarningCode;
import com.knowledge.common.enums.parse.SignalSubtype;
import com.knowledge.worker.parser.signal.SignalFallbackHandler;
import org.springframework.stereotype.Component;

/**
 * 纯矢量页降级处置：写 VECTOR_PAGE_SUSPECTED 告警 + 登记空白页（无文本无图片，同样不计失败单元、
 * 同样从成功占比的分母剔除），计 0 个失败单元。
 *
 * @author cxxl
 */
@Component
public class VectorPageFallbackHandler implements SignalFallbackHandler {

    @Override
    public SignalSubtype subtype() {
        return SignalSubtype.VECTOR_GRAPHIC;
    }

    @Override
    public int handle(Signal signal, QualityInfo quality) {
        String region = StrUtil.blankToDefault(signal.getRegion(), "");
        quality.getWarnings().add(QualityWarning.of(QualityWarningCode.VECTOR_PAGE_SUSPECTED, null, "WARN",
                region + " 无文本无图片但有矢量图形覆盖，按纯矢量页处理（不计失败）"));
        FallbackSupport.addBlankPage(quality, FallbackSupport.parsePage(region));
        return 0;
    }
}
