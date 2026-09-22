package com.knowledge.worker.parser.impl;

import cn.hutool.core.util.ObjectUtil;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.domain.parse.signal.ParseFact;
import com.knowledge.common.domain.parse.signal.Signal;
import com.knowledge.common.enums.parse.SignalSubtype;
import com.knowledge.common.enums.parse.SignalType;
import com.knowledge.worker.parser.ParseContext;
import com.knowledge.worker.parser.ParseProperties;
import com.knowledge.worker.parser.signal.SignalDetector;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 信号判定器实现：页级指标按阈值判定（先便宜后贵）+ 解析事实直接转信号。
 * 全链执行、聚合信号（命中不中断，一个文件可同时命中多类信号）。
 * 每个信号显式携带子类型（SignalSubtype），管线分支不依赖 evidence 文案。
 *
 * @author cxxl
 */
@Component
public class SignalDetectorImpl implements SignalDetector {

    @Override
    public List<Signal> detect(ParseSource nativeSource, ParseContext context) {
        List<Signal> signals = new ArrayList<>();
        if (ObjectUtil.isNull(nativeSource) || ObjectUtil.isNull(context.getProperties())) {
            return signals;
        }
        ParseProperties p = context.getProperties();

        // ① 页级指标 → 阈值信号（先便宜后贵：字符数 → 乱码率 → 文字占比）
        if (ObjectUtil.isNotNull(nativeSource.getPageMetrics())) {
            for (var metric : nativeSource.getPageMetrics()) {
                String page = "page " + metric.getPage();
                if (metric.getCharCount() < p.getScanPageMinChars()) {
                    signals.add(Signal.of(SignalType.OCR_TEXT, page,
                            "非空白字符数低于阈值", metric.getCharCount() + "<" + p.getScanPageMinChars(),
                            SignalSubtype.SCANNED));
                } else if (metric.getGarbledRatio() > p.getGarbledRateThreshold()) {
                    signals.add(Signal.of(SignalType.OCR_TEXT, page,
                            "乱码率超阈值", String.format("%.2f>%.2f", metric.getGarbledRatio(), p.getGarbledRateThreshold()),
                            SignalSubtype.GARBLED));
                } else if (metric.getTextAreaRatio() < p.getTextAreaRatioThreshold()) {
                    signals.add(Signal.of(SignalType.OCR_IMAGE, page,
                            "文字占比低于阈值", String.format("%.2f<%.2f", metric.getTextAreaRatio(), p.getTextAreaRatioThreshold()),
                            SignalSubtype.IMAGE_LOW_RATIO));
                }
            }
        }

        // ② 解析事实 → 信号（表格规则失败 / 版面异常 / 结构缺口；子类型按事实类型推导）
        if (ObjectUtil.isNotNull(nativeSource.getFacts())) {
            for (ParseFact fact : nativeSource.getFacts()) {
                signals.add(Signal.of(SignalType.valueOf(fact.getType()), fact.getRegion(), fact.getEvidence(), "-",
                        subtypeOfFact(fact.getType())));
            }
        }
        return signals;
    }

    /** 解析事实类型 → 信号子类型（OCR_IMAGE 事实均为嵌入图片；无对应子类型返回 null）。 */
    private SignalSubtype subtypeOfFact(String factType) {
        return switch (SignalType.valueOf(factType)) {
            case OCR_IMAGE -> SignalSubtype.IMAGE_EMBEDDED;
            case TABLE -> SignalSubtype.TABLE_RULE_FAILED;
            case LAYOUT -> SignalSubtype.LAYOUT_RULE_FAILED;
            case OCR_TEXT -> null;
        };
    }
}
