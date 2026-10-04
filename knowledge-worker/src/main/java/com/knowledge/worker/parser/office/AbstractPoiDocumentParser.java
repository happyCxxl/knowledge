package com.knowledge.worker.parser.office;

import cn.hutool.core.util.StrUtil;
import com.knowledge.common.domain.parse.ParseElement;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.domain.parse.signal.PageMetric;
import com.knowledge.common.domain.parse.signal.ParseFact;
import com.knowledge.common.enums.parse.ElementType;
import com.knowledge.common.enums.parse.SignalType;
import com.knowledge.common.utils.TextUtil;
import com.knowledge.worker.parser.DocumentParserPort;
import com.knowledge.worker.parser.ParseContext;
import com.knowledge.worker.parser.support.ParserStreamSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * POI 系解析器基类（非 Spring Bean）：能力标识、parse 骨架
 * （读全输入 → 建 native 源 → parseNative）与解析事实组装；输入流读取见 {@link ParserStreamSupport}。
 *
 * @author cxxl
 */
public abstract class AbstractPoiDocumentParser implements DocumentParserPort {

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

    /**
     * Word 系单元指标：整档正文文本按 pageChars 折算虚拟页（Azure 文档智能页单位口径），
     * 逐单元回填字符数、乱码率与内嵌图片数（图片无页归属，统一记在首个单元）。
     */
    protected void fillVirtualPageMetrics(ParseSource source, int pageChars) {
        String text = bodyText(source);
        List<String> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder();
        int budget = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            page.appendCodePoint(codePoint);
            if (!Character.isWhitespace(codePoint) && ++budget >= pageChars) {
                pages.add(page.toString());
                page = new StringBuilder();
                budget = 0;
            }
            i += Character.charCount(codePoint);
        }
        if (!page.isEmpty() || pages.isEmpty()) {
            pages.add(page.toString());
        }
        List<Integer> imageCounts = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            imageCounts.add(i == 0 ? imageCount(source) : 0);
        }
        fillUnitMetrics(source, pages, imageCounts);
    }

    /** 逐单元指标回填（Excel 每 worksheet = 1 单元）：单元数即判定单元数 */
    protected void fillUnitMetrics(ParseSource source, List<String> unitTexts, List<Integer> unitImageCounts) {
        List<PageMetric> metrics = new ArrayList<>();
        for (int i = 0; i < unitTexts.size(); i++) {
            String text = StrUtil.blankToDefault(unitTexts.get(i), "");
            PageMetric metric = new PageMetric();
            metric.setPage(i + 1);
            metric.setCharCount(nonBlankCount(text));
            metric.setGarbledRatio(TextUtil.garbledRatio(text));
            metric.setImageCount(i < unitImageCounts.size() ? unitImageCounts.get(i) : 0);
            metrics.add(metric);
        }
        source.setPageMetrics(metrics);
        source.setUnitCount(metrics.size());
    }

    /** 正文文本：正文元素的文本按顺序拼接（页眉页脚不计入单元指标） */
    protected static String bodyText(ParseSource source) {
        return source.getElements().stream()
                .filter(element -> !ElementType.HEADER.name().equals(element.getType())
                        && !ElementType.FOOTER.name().equals(element.getType()))
                .map(ParseElement::getText)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining("\n"));
    }

    /** 嵌入图片元素数 */
    protected static int imageCount(ParseSource source) {
        return (int) source.getElements().stream()
                .filter(element -> ElementType.IMAGE.name().equals(element.getType()))
                .count();
    }

    /** 非空白码点数 */
    private static int nonBlankCount(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            if (!Character.isWhitespace(codePoint)) {
                count++;
            }
            i += Character.charCount(codePoint);
        }
        return count;
    }
}
