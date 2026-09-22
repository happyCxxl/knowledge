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
 * 嵌入图片降级处置：结构缺口——嵌入图片仅引用，写告警不判失败（不计失败单元）。
 *
 * @author cxxl
 */
@Component
public class ImageEmbeddedFallbackHandler implements SignalFallbackHandler {

    @Override
    public SignalSubtype subtype() {
        return SignalSubtype.IMAGE_EMBEDDED;
    }

    @Override
    public int handle(Signal signal, QualityInfo quality) {
        String region = StrUtil.blankToDefault(signal.getRegion(), "");
        quality.getWarnings().add(QualityWarning.of(QualityWarningCode.IMAGE_TEXT_UNRECOGNIZED, null, "WARN",
                region + " 嵌入图片文字未识别（OCR 预留，一期仅记录引用与图注）"));
        return 0;
    }
}
