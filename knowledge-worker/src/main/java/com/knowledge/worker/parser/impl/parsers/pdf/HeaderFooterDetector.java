package com.knowledge.worker.parser.impl.parsers.pdf;

import cn.hutool.core.util.StrUtil;
import com.knowledge.worker.parser.ParseContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PDF 页眉页脚识别（package-private，PdfBoxDocumentParser 专用助手）：
 * 页顶/页底区域内按文本聚合，出现页数达 headerMinPages 即判页眉/页脚；
 * 页脚剥离行尾页码后按"基础文本"聚合；纯页码行共享一键、全文只产一条页脚元素。
 *
 * @author cxxl
 */
public final class HeaderFooterDetector {

    /** 页码行共享键（纯页码行全文只产一条页脚元素） */
    public static final String PAGE_NUMBER_KEY = "__page_number__";

    private HeaderFooterDetector() {
    }

    /** 页眉识别：页顶 headerAreaRatio 区域内按文本聚合，出现页数达 headerMinPages 即判页眉。 */
    public static Map<String, HeaderLine> detectHeaders(List<PageContent> pages, ParseContext context) {
        double headerBand = context.getProperties().getHeaderAreaRatio();
        // text → 出现页列表
        Map<String, List<PageLine>> occurrences = new LinkedHashMap<>();
        for (PageContent page : pages) {
            double bandY = page.pageHeight() * headerBand;
            for (PageLine line : page.lines()) {
                if (line.y() < bandY && StrUtil.isNotBlank(line.text().trim())) {
                    occurrences.computeIfAbsent(line.text().trim(), k -> new ArrayList<>()).add(line);
                }
            }
        }
        Map<String, HeaderLine> headers = new HashMap<>();
        int minPages = context.getProperties().getHeaderMinPages();
        occurrences.forEach((text, lines) -> {
            if (lines.size() >= minPages) {
                headers.put(text, new HeaderLine());
            }
        });
        return headers;
    }

    /**
     * 页脚识别（与页眉同套）：页底 footerAreaRatio 区域内按"基础文本"聚合——
     * 页脚常与页码同行（"XX项目 3"），页码使每页行文本不同，需剥离行尾页码后再判多页重复；
     * 纯页码行（纯数字/第X页）直接判页脚（共享一个键，全文只产一条）。
     */
    public static Map<String, HeaderLine> detectFooters(List<PageContent> pages, ParseContext context) {
        double footerBand = context.getProperties().getFooterAreaRatio();
        Map<String, List<PageLine>> occurrences = new LinkedHashMap<>();
        for (PageContent page : pages) {
            double bandY = page.pageHeight() * (1 - footerBand);
            for (PageLine line : page.lines()) {
                if (line.y() + line.height() < bandY) {
                    continue;
                }
                String text = line.text().trim();
                if (StrUtil.isBlank(text)) {
                    continue;
                }
                occurrences.computeIfAbsent(footerBase(text), k -> new ArrayList<>()).add(line);
            }
        }
        Map<String, HeaderLine> footers = new HashMap<>();
        int minPages = context.getProperties().getHeaderMinPages();
        occurrences.forEach((text, lines) -> {
            if (PAGE_NUMBER_KEY.equals(text) || lines.size() >= minPages) {
                footers.put(text, new HeaderLine());
            }
        });
        return footers;
    }

    /**
     * 页脚基础文本：纯页码行 → 共享键；否则剥离行尾页码（"XX项目 3"→"XX项目"），
     * 无行尾页码则原样（按完整文本聚合）。
     */
    public static String footerBase(String text) {
        if (isPageNumberLine(text)) {
            return PAGE_NUMBER_KEY;
        }
        String stripped = text.replaceAll("\\s*(第\\s*[0-9一二三四五六七八九十百]+\\s*页|[-–—]?\\d{1,4}[-–—]?)$", "");
        return StrUtil.isBlank(stripped) ? text : stripped;
    }

    /** 页码模式：纯数字（可带 - 装饰）或 第X页 */
    private static boolean isPageNumberLine(String text) {
        return text.matches("^[-–—]?\\s*\\d{1,4}\\s*[-–—]?$")
                || text.matches("^第\\s*[0-9一二三四五六七八九十百]+\\s*页$");
    }

    /** 页眉/页脚命中记录：是否待产出元素（markEmitted 后置为已产出，防每页重复产出） */
    public static final class HeaderLine {
        private boolean emitted;

        HeaderLine() {
        }

        /** 是否待产出（尚未产出元素）。 */
        public boolean pending() {
            return !emitted;
        }

        public void markEmitted() {
            this.emitted = true;
        }
    }
}
