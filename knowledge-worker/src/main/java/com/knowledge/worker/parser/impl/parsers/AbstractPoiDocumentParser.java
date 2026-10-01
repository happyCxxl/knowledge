package com.knowledge.worker.parser.impl.parsers;

import com.knowledge.common.domain.parse.ParseElement;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.domain.parse.signal.ParseFact;
import com.knowledge.common.enums.parse.SignalType;
import com.knowledge.worker.parser.DocumentParserPort;
import com.knowledge.worker.parser.ParseContext;

import java.util.ArrayList;

/**
 * POI 系解析器公共基类（package-private，非 Spring Bean）：
 * 统一能力标识（capabilityName/Version）、"读全输入 → 建 native 源 → 解析主流程"骨架与解析事实组装。
 * 输入流读取共用 {@link ParserStreamSupport}。
 *
 * @author cxxl
 */
abstract class AbstractPoiDocumentParser implements DocumentParserPort {

    private static final String CAPABILITY = "poi";
    private static final String CAPABILITY_VERSION = "5.4.0";

    @Override
    public final ParseSource parse(ParseContext context) {
        byte[] data = ParserStreamSupport.readAll(context);
        ParseSource source = ParseSource.nativeSource(capabilityName() + "-" + capabilityVersion());
        parseNative(source, data, context);
        return source;
    }

    /**
     * 原生结构解析主流程（输入已读全、source 已带能力标识）。
     *
     * @param source  待填充的原生解析结果
     * @param data    文件字节（已读全）
     * @param context 解析上下文（文件引用等）
     */
    protected abstract void parseNative(ParseSource source, byte[] data, ParseContext context);

    /** 表格元素追加单元格（cells 惰性建表，各格式表格单元格统一入口） */
    protected static void appendCell(ParseElement tableElement, ParseElement cellElement) {
        if (tableElement.getCells() == null) {
            tableElement.setCells(new ArrayList<>());
        }
        tableElement.getCells().add(cellElement);
    }

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
