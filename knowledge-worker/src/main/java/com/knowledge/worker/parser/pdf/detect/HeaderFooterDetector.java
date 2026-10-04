package com.knowledge.worker.parser.pdf.detect;

import cn.hutool.core.util.StrUtil;
import com.knowledge.worker.parser.ParseContext;

import com.knowledge.worker.parser.pdf.model.PageContent;
import com.knowledge.worker.parser.pdf.model.PageLine;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PDF 页眉页脚识别（PdfBoxDocumentParser 专用助手）：
 * 页顶/页底区域内按文本聚合，出现页数达阈值即判页眉/页脚（同一页重复出现只计一页）；
 * 页脚剥离行尾页码后按"基础文本"聚合；页码按「形态 + 页底带 + 跨页同址」判定，同页多条纯数字行不判。
 *
 * @author cxxl
 */
public final class HeaderFooterDetector {

    /** 页码行共享键（页码元素按页各产一条） */
    public static final String PAGE_NUMBER_KEY = "__page_number__";

    /** 页码同址判定容差（pt） */
    private static final double PAGE_NUMBER_X_TOLERANCE = 5.0;

    /** 页码跨页判定所需的最少页数 */
    private static final int PAGE_NUMBER_MIN_PAGES = 2;

    /**
     * 比较键空白归一化：去首尾空白、连续空白压成一个空格、去掉零宽字符。
     * 只用于文本比对，不改元素里的文本。
     */
    public static String normalizeKey(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        return text.replaceAll("[\\u200B-\\u200D\\uFEFF]", "")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private HeaderFooterDetector() {
    }

    /** 页眉识别：页顶 headerAreaRatio 区域内按文本聚合，出现页数达阈值即判页眉。 */
    public static Set<String> detectHeaders(List<PageContent> pages, ParseContext context) {
        double headerBand = context.getProperties().getHeaderAreaRatio();
        Map<String, Set<Integer>> pagesByText = new LinkedHashMap<>();
        for (PageContent page : pages) {
            double bandY = page.pageHeight() * headerBand;
            for (PageLine line : page.lines()) {
                String text = normalizeKey(line.text());
                if (line.y() < bandY && StrUtil.isNotBlank(text)) {
                    pagesByText.computeIfAbsent(text, k -> new HashSet<>()).add(page.pageNo());
                }
            }
        }
        int minPages = context.getProperties().getRunningTextMinPages();
        Set<String> headers = new LinkedHashSet<>();
        pagesByText.forEach((text, pageNos) -> {
            if (pageNos.size() >= minPages) {
                headers.add(text);
            }
        });
        return headers;
    }

    /**
     * 页脚识别（与页眉同套）：页底 footerAreaRatio 区域内按"基础文本"聚合——
     * 页脚常与页码同行（"XX项目 3"），页码使每页行文本不同，需剥离行尾页码后再判多页重复；
     * 纯页码行走页码模式：同页至多一条，且跨页中心在容差内成组、组内页数达阈值才判。
     */
    public static FooterKeys detectFooters(List<PageContent> pages, ParseContext context) {
        double footerBand = context.getProperties().getFooterAreaRatio();
        Map<String, Set<Integer>> pagesByText = new LinkedHashMap<>();
        Map<Integer, PageLine> pageNumberLineByPage = new LinkedHashMap<>();
        Map<Integer, Integer> pageNumberCountByPage = new LinkedHashMap<>();
        for (PageContent page : pages) {
            double bandY = page.pageHeight() * (1 - footerBand);
            for (PageLine line : page.lines()) {
                if (line.y() + line.height() < bandY) {
                    continue;
                }
                String text = normalizeKey(line.text());
                if (StrUtil.isBlank(text)) {
                    continue;
                }
                if (isPageNumberLine(text)) {
                    pageNumberCountByPage.merge(page.pageNo(), 1, Integer::sum);
                    pageNumberLineByPage.putIfAbsent(page.pageNo(), line);
                    continue;
                }
                pagesByText.computeIfAbsent(footerBase(text), k -> new HashSet<>()).add(page.pageNo());
            }
        }
        int minPages = context.getProperties().getRunningTextMinPages();
        Set<String> texts = new LinkedHashSet<>();
        pagesByText.forEach((text, pageNos) -> {
            if (pageNos.size() >= minPages) {
                texts.add(text);
            }
        });
        return new FooterKeys(texts,
                pageNumberCenters(pageNumberLineByPage, pageNumberCountByPage, minPages));
    }

    /** 页码模式确认：同页至多一条，跨页中心容差内成组，组内页数达阈值即记该中心 */
    private static List<Double> pageNumberCenters(Map<Integer, PageLine> lineByPage,
                                                  Map<Integer, Integer> countByPage, int minPages) {
        List<Double> centers = new ArrayList<>();
        if (countByPage.values().stream().anyMatch(count -> count > 1)) {
            return centers;
        }
        List<PageLine> candidates = new ArrayList<>(lineByPage.values());
        boolean[] used = new boolean[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            if (used[i]) {
                continue;
            }
            double center = centerOf(candidates.get(i));
            Set<Integer> groupPages = new HashSet<>();
            for (int j = i; j < candidates.size(); j++) {
                if (!used[j] && Math.abs(centerOf(candidates.get(j)) - center) <= PAGE_NUMBER_X_TOLERANCE) {
                    used[j] = true;
                    groupPages.add(candidates.get(j).page());
                }
            }
            if (groupPages.size() >= Math.max(PAGE_NUMBER_MIN_PAGES, minPages)) {
                centers.add(center);
            }
        }
        return centers;
    }

    /** 行横向中心（页码同址判定口径） */
    private static double centerOf(PageLine line) {
        return line.x() + line.width() / 2;
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

    /** 页脚判定结果：重复文本基础键 + 已确认的页码横向中心（页码按行位置命中） */
    public record FooterKeys(Set<String> texts, List<Double> pageNumberCenters) {

        /** 行是否命中页脚：基础文本键命中，或页码中心命中 */
        public boolean matches(String footerBaseKey, PageLine line) {
            if (texts.contains(footerBaseKey)) {
                return true;
            }
            if (!PAGE_NUMBER_KEY.equals(footerBaseKey)) {
                return false;
            }
            double center = centerOf(line);
            for (double candidate : pageNumberCenters) {
                if (Math.abs(candidate - center) <= PAGE_NUMBER_X_TOLERANCE) {
                    return true;
                }
            }
            return false;
        }
    }
}
