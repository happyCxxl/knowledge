package com.knowledge.worker.parser.impl;

import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.domain.parse.signal.PageMetric;
import com.knowledge.common.domain.parse.signal.ParseFact;
import com.knowledge.common.domain.parse.signal.Signal;
import com.knowledge.common.enums.parse.SignalSubtype;
import com.knowledge.common.enums.parse.SignalType;
import com.knowledge.common.utils.NullUtil;
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
        if (NullUtil.isNull(nativeSource) || NullUtil.isNull(context.getProperties())) {
            return signals;
        }
        ParseProperties p = context.getProperties();

        // ① 单元级指标 → 阈值信号（先便宜后贵：整单元无文本 → 文字极少/文字占比低 → 乱码率）
        if (NullUtil.isNotNull(nativeSource.getPageMetrics())) {
            for (var metric : nativeSource.getPageMetrics()) {
                String page = "page " + metric.getPage();
                String imageEvidence = imageEvidence(metric, p);
                if (metric.getCharCount() <= 0) {
                    // 整单元无文本：按图片口径分流扫描单元与空白单元
                    if (hasImage(metric, p)) {
                        signals.add(Signal.of(SignalType.OCR_TEXT, page,
                                "整单元无文本且含图片（扫描单元）", imageEvidence, SignalSubtype.SCANNED));
                    } else {
                        signals.add(Signal.of(SignalType.OCR_TEXT, page,
                                "整单元无文本且无图片（空白单元）", imageEvidence, SignalSubtype.BLANK));
                    }
                } else if ((metric.getTextAreaRatio() > 0
                        && metric.getTextAreaRatio() < p.getTextAreaRatioThreshold())
                        || (metric.getImageCount() > 0 && metric.getCharCount() < p.getScanPageMinChars())) {
                    signals.add(Signal.of(SignalType.OCR_IMAGE, page,
                            "文字极少或文字占比低（疑似图片单元）",
                            "chars=" + metric.getCharCount() + "," + imageEvidence,
                            SignalSubtype.IMAGE_LOW_RATIO));
                } else if (metric.getGarbledRatio() > p.getGarbledRateThreshold()) {
                    signals.add(Signal.of(SignalType.OCR_TEXT, page,
                            "乱码率超阈值", String.format("%.2f>%.2f", metric.getGarbledRatio(), p.getGarbledRateThreshold()),
                            SignalSubtype.GARBLED));
                }
            }
        }

        // ② 解析事实 → 信号（表格规则失败 / 版面异常 / 结构缺口；子类型按事实类型推导）
        if (NullUtil.isNotNull(nativeSource.getFacts())) {
            for (ParseFact fact : nativeSource.getFacts()) {
                signals.add(Signal.of(SignalType.valueOf(fact.getType()), fact.getRegion(), fact.getEvidence(), "-",
                        subtypeOfFact(fact.getType())));
            }
        }
        return signals;
    }

    /**
     * 单元是否按"图片单元"处理：PDF 看图片覆盖面积，Office 看内嵌图片数
     * （两条口径由解析器互斥回填：PDF 只填面积、Office 只填图片数）。
     */
    private boolean hasImage(PageMetric metric, ParseProperties properties) {
        return metric.getImageAreaRatio() >= properties.getScanPageImageRatio() || metric.getImageCount() > 0;
    }

    /** 图片口径证据文案（面积门槛与内嵌图片数） */
    private String imageEvidence(PageMetric metric, ParseProperties properties) {
        return String.format("imageArea=%.2f(≥%.2f),imageCount=%d",
                metric.getImageAreaRatio(), properties.getScanPageImageRatio(), metric.getImageCount());
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
