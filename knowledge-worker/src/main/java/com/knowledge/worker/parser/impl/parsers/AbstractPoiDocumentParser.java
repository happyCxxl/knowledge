package com.knowledge.worker.parser.impl.parsers;

import com.knowledge.common.domain.parse.signal.ParseFact;
import com.knowledge.common.enums.parse.SignalType;
import com.knowledge.worker.parser.DocumentParserPort;

/**
 * POI 系解析器公共基类（package-private，非 Spring Bean）：
 * 统一能力标识（capabilityName/Version）与解析事实组装。
 * 输入流读取共用 {@link ParserStreamSupport}。
 *
 * @author cxxl
 */
abstract class AbstractPoiDocumentParser implements DocumentParserPort {

    private static final String CAPABILITY = "poi";
    private static final String CAPABILITY_VERSION = "5.4.0";

    @Override
    public String capabilityName() {
        return CAPABILITY;
    }

    @Override
    public String capabilityVersion() {
        return CAPABILITY_VERSION;
    }

    /** 组装嵌入图片解析事实信号（OCR_IMAGE，图片仅引用无文字，由管线判定器汇总）。 */
    protected ParseFact fact(String region, String evidence) {
        ParseFact fact = new ParseFact();
        fact.setType(SignalType.OCR_IMAGE.name());
        fact.setRegion(region);
        fact.setEvidence(evidence);
        return fact;
    }
}
