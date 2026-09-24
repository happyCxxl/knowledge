package com.knowledge.worker.parser.impl.parsers;

import cn.hutool.core.util.ObjectUtil;
import com.knowledge.common.domain.parse.*;
import com.knowledge.common.domain.parse.signal.PageMetric;
import com.knowledge.common.domain.parse.signal.ParseFact;
import com.knowledge.common.enums.input.FileFormat;
import com.knowledge.common.enums.parse.ElementType;
import com.knowledge.common.enums.parse.SignalType;
import com.knowledge.common.utils.TextUtil;
import com.knowledge.worker.parser.DocumentParserPort;
import com.knowledge.worker.parser.ParseContext;
import com.knowledge.worker.parser.impl.parsers.pdf.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * PDFBox 原生解析器（数字 PDF）：逐页抽字符 + 坐标 + 字体 → 行聚合 → 段落聚合；
 * 表格规则检测（列 x 对齐启发式，规则失败出 TABLE 事实）、页级信号（字符数/乱码率/文字占比）、
 * 多页同位置重复文本标记 HEADER、页码 + bbox 溯源。
 * 页眉页脚识别与表格候选检测已拆至 {@link HeaderFooterDetector}/{@link TableCandidateDetector} 助手。
 *
 * <p>一期简化：表格检测用"列对齐聚类"启发式（规则线图形扫描随表格模型能力评估）；
 * 无边框/复杂表格聚类失败时出 TABLE 事实，由管线降级为段落 + 告警。
 *
 * @author cxxl
 */
@Slf4j
@Component
public class PdfBoxDocumentParser implements DocumentParserPort {

    private static final String CAPABILITY = "pdfbox";
    private static final String CAPABILITY_VERSION = "3.0.4";

    /**
     * 页底截断判定：行底边进入页面底部比例
     */
    private static final double PAGE_BOTTOM_RATIO = 0.95;

    /**
     * token 分词的空隙阈值（字符 x 间距 ÷ 字号）
     */
    private static final double TOKEN_GAP_RATIO = 0.35;

    @Override
    public boolean supports(String mimeType) {
        return FileFormat.PDF.getMimeType().equals(mimeType);
    }

    @Override
    public String capabilityName() {
        return CAPABILITY;
    }

    @Override
    public String capabilityVersion() {
        return CAPABILITY_VERSION;
    }

    @Override
    public ParseSource parse(ParseContext context) {
        byte[] data = ParserStreamSupport.readAll(context);
        String fileId = context.getFileRef().getFileId();
        ParseSource source = ParseSource.nativeSource(CAPABILITY + "-" + CAPABILITY_VERSION);
        try (PDDocument doc = Loader.loadPDF(data)) {
            int pageCount = doc.getNumberOfPages();
            source.setUnitCount(pageCount);
            context.getFileRef().setPageCount(pageCount);

            // ① 逐页提取：字符 + 页级指标
            List<PageContent> pages = new ArrayList<>();
            for (int p = 1; p <= pageCount; p++) {
                PageContent pageContent = extractPage(doc, p, context);
                pages.add(pageContent);
                source.getPageMetrics().add(pageContent.metric());
                source.getPageDimensions().add(new PageDimension(pageContent.pageNo(),
                        pageContent.pageWidth(), pageContent.pageHeight()));
            }

            // ② HEADER/FOOTER 识别：页顶/页底多页同位置重复文本（先算，正文聚合时排除）
            Map<String, HeaderFooterDetector.HeaderLine> headers = HeaderFooterDetector.detectHeaders(pages, context);
            Map<String, HeaderFooterDetector.HeaderLine> footers = HeaderFooterDetector.detectFooters(pages, context);

            // ③ 逐页组装元素：段落聚合 + 表格启发式（按 y 顺序保持阅读顺序）
            List<ParseElement> elements = new ArrayList<>();
            for (PageContent page : pages) {
                assemblePageElements(page, headers, footers, elements, source, fileId, context.getProperties());
            }
            source.setElements(elements);
        } catch (Exception e) {
            log.warn("PDF 解析失败, fileId={}", fileId, e);
            throw new IllegalStateException("PDF 解析失败: " + e.getMessage(), e);
        }
        return source;
    }

    // ---------------- 页提取 ----------------

    /**
     * 单页提取：字符级指标 → 行聚合（y 容差）→ token 分词 → 文字占比（行级 bbox 累计，防误伤稀疏页）。
     */
    private PageContent extractPage(PDDocument doc, int pageNo, ParseContext context) throws IOException {
        PositionStripper stripper = new PositionStripper();
        stripper.setStartPage(pageNo);
        stripper.setEndPage(pageNo);
        stripper.getText(doc);

        PDPage page = doc.getPage(pageNo - 1);
        double pageWidth = page.getMediaBox().getWidth();
        double pageHeight = page.getMediaBox().getHeight();

        // 页级指标（字符统计先行；文字占比改在行聚合后按行级 bbox 计算，见下方）
        PageMetric metric = new PageMetric();
        metric.setPage(pageNo);
        int totalChars = stripper.chars.size();
        int nonBlank = 0;
        int nonCommon = 0;
        for (CharInfo c : stripper.chars) {
            if (!Character.isWhitespace(c.codePoint)) {
                nonBlank++;
                if (TextUtil.isNonCommonChar(c.codePoint)) {
                    nonCommon++;
                }
            }
        }
        metric.setCharCount(nonBlank);
        metric.setGarbledRatio(totalChars > 0 ? (double) nonCommon / totalChars : 0);

        // 行聚合（y 容差聚类 → 行内按 x 排序 → token 分词）
        List<CharInfo> sorted = stripper.chars.stream()
                .sorted(Comparator.comparingDouble(CharInfo::y).thenComparingDouble(CharInfo::x))
                .toList();
        List<PageLine> lines = new ArrayList<>();
        List<CharInfo> lineChars = new ArrayList<>();
        double lineY = -1;
        for (CharInfo c : sorted) {
            if (lineChars.isEmpty()) {
                lineChars.add(c);
                lineY = c.y;
            } else if (Math.abs(c.y - lineY) <= context.getProperties().getLineYTolerance()) {
                lineChars.add(c);
                lineY = (lineY + c.y) / 2;
            } else {
                lines.add(buildLine(pageNo, lineChars));
                lineChars = new ArrayList<>();
                lineChars.add(c);
                lineY = c.y;
            }
        }
        if (!lineChars.isEmpty()) {
            lines.add(buildLine(pageNo, lineChars));
        }

        // 文字占比：行级 bbox 累计（行宽 × 行高，行高取 max(字符高, 字号×1.2)），
        // 字符级小盒子累计会低估占版面积、误伤稀疏页
        double textArea = 0;
        for (PageLine line : lines) {
            double lineHeight = Math.max(line.height(), line.fontSize() * 1.2);
            textArea += line.width() * lineHeight;
        }
        metric.setTextAreaRatio(pageWidth * pageHeight > 0 ? textArea / (pageWidth * pageHeight) : 0);

        return new PageContent(pageNo, pageWidth, pageHeight, metric, lines);
    }

    /**
     * 行构建：行内 x 排序 → token 分词（字符间距超阈值断词）→ 目录行特征判定。
     */
    private PageLine buildLine(int pageNo, List<CharInfo> lineChars) {
        lineChars.sort(Comparator.comparingDouble(c -> c.x));
        double x = lineChars.getFirst().x;
        double y = lineChars.stream().mapToDouble(c -> c.y).min().orElse(0);
        double maxX = lineChars.stream().mapToDouble(c -> c.x + c.width).max().orElse(0);
        double maxY = lineChars.stream().mapToDouble(c -> c.y + c.height).max().orElse(0);
        double fontSize = lineChars.stream().mapToDouble(c -> c.fontSize).average().orElse(0);
        CharInfo first = lineChars.getFirst();
        // token 分词：字符间距超阈值即断（空白字符不进入 token，自然形成词间断点）
        List<Token> tokens = new ArrayList<>();
        StringBuilder tokenText = new StringBuilder();
        double tokenStart = -1;
        double tokenEnd = 0;
        CharInfo prev = null;
        for (CharInfo c : lineChars) {
            if (Character.isWhitespace(c.codePoint)) {
                continue;
            }
            if (ObjectUtil.isNull(prev)) {
                tokenStart = c.x;
            }
            double gap = ObjectUtil.isNull(prev) ? 0 : c.x - (prev.x + prev.width);
            if (ObjectUtil.isNotNull(prev) && gap > TOKEN_GAP_RATIO * Math.max(c.fontSize, 1)) {
                tokens.add(new Token(tokenText.toString(), tokenStart, tokenEnd));
                tokenText = new StringBuilder();
                tokenStart = c.x;
            }
            tokenText.appendCodePoint(c.codePoint);
            tokenEnd = c.x + c.width;
            prev = c;
        }
        if (!tokenText.isEmpty()) {
            tokens.add(new Token(tokenText.toString(), tokenStart, tokenEnd));
        }
        String text = String.join("", tokens.stream().map(Token::text).toList());
        boolean tocCandidate = TocLineFeature.isTocLine(text);
        return new PageLine(pageNo, x, y, maxX - x, maxY - y, text, fontSize,
                first.fontName, first.bold, tokens, tocCandidate);
    }

    /**
     * 单页元素组装：页眉/页脚元素 → 正文按 y 顺序推进（表格候选块与段落互斥结算）。
     */
    private void assemblePageElements(PageContent page, Map<String, HeaderFooterDetector.HeaderLine> headers,
                                      Map<String, HeaderFooterDetector.HeaderLine> footers,
                                      List<ParseElement> elements, ParseSource source, String fileId,
                                      com.knowledge.worker.parser.ParseProperties properties) {
        // 页首 HEADER 元素（按本页首见顺序）
        for (PageLine line : page.lines()) {
            String key = line.text().trim();
            if (headers.containsKey(key) && headers.get(key).pending()) {
                HeaderFooterDetector.HeaderLine header = headers.get(key);
                ParseElement element = ParseElement.of("h" + page.pageNo() + "_" + Integer.toHexString(key.hashCode()),
                        ElementType.HEADER);
                element.setText(key);
                element.setPage(page.pageNo());
                element.setBbox(new BBox(line.x(), line.y(), line.width(), line.height()));
                element.setProvenance(new Provenance(fileId, "pdf#top-area"));
                elements.add(element);
                header.markEmitted();
            }
        }

        // 页底 FOOTER 元素（多页重复文本按首见产出；页码模式全文一条；文本取基础文本）
        for (PageLine line : page.lines()) {
            String key = line.text().trim();
            String footerKey = HeaderFooterDetector.footerBase(key);
            HeaderFooterDetector.HeaderLine footer = footers.get(footerKey);
            if (footer != null && footer.pending()) {
                ParseElement element = ParseElement.of(
                        "f" + page.pageNo() + "_" + Integer.toHexString(footerKey.hashCode()),
                        ElementType.FOOTER);
                element.setText(HeaderFooterDetector.PAGE_NUMBER_KEY.equals(footerKey) ? key : footerKey);
                element.setPage(page.pageNo());
                element.setBbox(new BBox(line.x(), line.y(), line.width(), line.height()));
                element.setProvenance(new Provenance(fileId, "pdf#bottom-area"));
                elements.add(element);
                footer.markEmitted();
            }
        }

        // 正文：按 y 顺序推进；多 token 连续行尝试表格块，否则段落聚合
        double footerBandY = page.pageHeight() * (1 - properties.getFooterAreaRatio());
        List<PageLine> bodyLines = page.lines().stream()
                .filter(line -> !headers.containsKey(line.text().trim()))
                .filter(line -> {
                    boolean inFooterBand = line.y() + line.height() >= footerBandY;
                    return !(inFooterBand && footers.containsKey(HeaderFooterDetector.footerBase(line.text().trim())));
                })
                .sorted(Comparator.comparingDouble(PageLine::y))
                .toList();

        List<PageLine> block = new ArrayList<>();
        List<PageLine> paragraph = new ArrayList<>();
        PageLine prev = null;
        for (PageLine line : bodyLines) {
            if (ObjectUtil.isNotNull(prev)
                    && verticalGap(prev, line) > paragraphGapThreshold(prev, properties)) {
                // 行距超阈值：先结算段落；表格候选块允许更大行距（表格行距可达数倍行高），
                // 仅当新行不再具备宽列距（非表格候选）时才结算表格块
                if (!paragraph.isEmpty()) {
                    elements.add(toParagraphElement(paragraph, fileId));
                    paragraph = new ArrayList<>();
                }
                if (!block.isEmpty() && !TableCandidateDetector.isCandidate(line)) {
                    flushTableBlock(block, page, elements, source, fileId);
                    block = new ArrayList<>();
                }
            }
            if (TableCandidateDetector.isCandidate(line)) {
                if (!paragraph.isEmpty()) {
                    elements.add(toParagraphElement(paragraph, fileId));
                    paragraph = new ArrayList<>();
                }
                block.add(line);
            } else {
                if (!block.isEmpty()) {
                    flushTableBlock(block, page, elements, source, fileId);
                    block = new ArrayList<>();
                }
                paragraph.add(line);
            }
            prev = line;
        }
        if (!block.isEmpty()) {
            flushTableBlock(block, page, elements, source, fileId);
        }
        if (!paragraph.isEmpty()) {
            elements.add(toParagraphElement(paragraph, fileId));
        }
    }

    private double verticalGap(PageLine upper, PageLine lower) {
        return lower.y() - (upper.y() + upper.height());
    }

    private double paragraphGapThreshold(PageLine line,
                                         com.knowledge.worker.parser.ParseProperties properties) {
        return line.fontSize() * properties.getParagraphGapRatio();
    }

    /**
     * 结算表格候选块：形成表格则出 TABLE 元素；否则出 TABLE 事实并把块降级为段落
     */
    private void flushTableBlock(List<PageLine> block, PageContent page, List<ParseElement> elements,
                                 ParseSource source, String fileId) {
        if (block.size() < 2) {
            elements.add(toParagraphElement(block, fileId));
            return;
        }
        List<List<Token>> tokenMatrix = block.stream().map(PageLine::tokens).toList();
        List<Double> columnX = TableCandidateDetector.clusterColumns(tokenMatrix);
        if (columnX.size() < TableCandidateDetector.MIN_TOKENS
                || !TableCandidateDetector.enoughLinesCoverColumns(tokenMatrix, columnX)) {
            // 表格规则失败：出 TABLE 事实，区域降级为段落
            ParseFact fact = new ParseFact();
            fact.setType(SignalType.TABLE.name());
            fact.setRegion("page " + page.pageNo());
            fact.setEvidence("列对齐聚类失败（疑似无边框/复杂表格）");
            source.getFacts().add(fact);
            elements.add(toParagraphElement(block, fileId));
            return;
        }
        ParseElement table = ParseElement.of("t" + page.pageNo() + "_" + Integer.toHexString(block.hashCode()),
                ElementType.TABLE);
        table.setPage(page.pageNo());
        double x = block.stream().mapToDouble(PageLine::x).min().orElse(0);
        double y = block.stream().mapToDouble(PageLine::y).min().orElse(0);
        double maxX = block.stream().mapToDouble(l -> l.x() + l.width()).max().orElse(0);
        double maxY = block.stream().mapToDouble(l -> l.y() + l.height()).max().orElse(0);
        table.setBbox(new BBox(x, y, maxX - x, maxY - y));
        table.setRows(block.size());
        table.setCols(columnX.size());
        table.setHeaderRow(0);
        table.setCutAtPageBottom(maxY >= page.pageHeight() * PAGE_BOTTOM_RATIO);
        List<ParseElement> cells = new ArrayList<>();
        for (int r = 0; r < block.size(); r++) {
            PageLine line = block.get(r);
            for (int c = 0; c < columnX.size(); c++) {
                Token token = TableCandidateDetector.findTokenInColumn(line.tokens(), columnX.get(c));
                ParseElement cell = ParseElement.of("t" + page.pageNo() + "_" + r + "_" + c, ElementType.TABLE_CELL);
                cell.setText(ObjectUtil.isNull(token) ? "" : token.text());
                cell.setRow(r);
                cell.setCol(c);
                cell.setIsHeader(r == 0);
                // 单元格 bbox：列 x 范围 × 行带高度（组装环节续表列宽模式放宽规则输入）
                double cellX = columnX.get(c);
                double cellWidth = (c + 1 < columnX.size() ? columnX.get(c + 1) : (line.x() + line.width())) - cellX;
                cell.setBbox(new BBox(cellX, line.y(), Math.max(cellWidth, 0), line.height()));
                cell.setProvenance(new Provenance(fileId, "pdf#page(" + page.pageNo() + ")/table[0]/cell[" + r + "," + c + "]"));
                cells.add(cell);
            }
        }
        table.setCells(cells);
        elements.add(table);
    }

    /**
     * 段落元素组装：多行聚合 + 字体事实（取首行）+ 目录行特征。
     */
    private ParseElement toParagraphElement(List<PageLine> lines, String fileId) {
        PageLine first = lines.getFirst();
        PageLine last = lines.getLast();
        ParseElement element = ParseElement.of("p" + first.page() + "_" + lines.hashCode(), ElementType.PARAGRAPH);
        element.setText(String.join("", lines.stream().map(PageLine::text).toList()));
        element.setPage(first.page());
        double x = lines.stream().mapToDouble(PageLine::x).min().orElse(0);
        double y = first.y();
        double maxX = lines.stream().mapToDouble(l -> l.x() + l.width()).max().orElse(0);
        double maxY = last.y() + last.height();
        element.setBbox(new BBox(x, y, maxX - x, maxY - y));
        FontInfo font = new FontInfo();
        font.setName(first.fontName());
        font.setSize(first.fontSize());
        font.setBold(first.bold());
        element.setFont(font);
        element.setTocCandidate(lines.stream().anyMatch(PageLine::tocCandidate));
        element.setProvenance(new Provenance(fileId, "pdf#page(" + first.page() + ")"));
        return element;
    }

    // ---------------- 内部结构 ----------------

    private static final class PositionStripper extends PDFTextStripper {
        private final List<CharInfo> chars = new ArrayList<>();

        PositionStripper() {
            super();
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) {
            for (TextPosition tp : textPositions) {
                String unicode = tp.getUnicode();
                if (ObjectUtil.isNull(unicode)) {
                    continue;
                }
                boolean bold = tp.getFont().getName().toLowerCase(Locale.ROOT).contains("bold");
                for (int i = 0; i < unicode.length(); ) {
                    int cp = unicode.codePointAt(i);
                    chars.add(new CharInfo(tp.getXDirAdj(), tp.getYDirAdj(), tp.getWidthDirAdj(),
                            tp.getHeightDir(), tp.getFontSizeInPt(), tp.getFont().getName(), bold, cp));
                    i += Character.charCount(cp);
                }
            }
        }
    }

    private record CharInfo(double x, double y, double width, double height, double fontSize,
                            String fontName, boolean bold, int codePoint) {
    }
}
