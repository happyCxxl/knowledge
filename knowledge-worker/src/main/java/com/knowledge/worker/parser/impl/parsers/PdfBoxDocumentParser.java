package com.knowledge.worker.parser.impl.parsers;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.common.domain.parse.*;
import com.knowledge.common.domain.parse.signal.PageMetric;
import com.knowledge.common.domain.parse.signal.ParseFact;
import com.knowledge.common.enums.input.FileFormat;
import com.knowledge.common.enums.parse.ElementType;
import com.knowledge.common.enums.parse.SignalType;
import com.knowledge.common.utils.TextUtil;
import com.knowledge.worker.parser.DocumentParserPort;
import com.knowledge.worker.parser.ParseContext;
import com.knowledge.worker.parser.ParseProperties;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * PDFBox 原生解析器（数字 PDF）：逐页抽字符 + 坐标 + 字体 → 行聚合 → 段落聚合；
 * 表格双路径检测（页面线条求线网格 → 有边框表；列对齐聚类 → 无边框表）、页级信号（字符数/乱码率/文字占比）、
 * 多页同位置重复文本标记 HEADER、页码 + bbox 溯源。
 * 页眉页脚识别、表格候选检测与页面线条已拆至 {@link HeaderFooterDetector}/{@link TableCandidateDetector}/{@link RuleLines} 助手。
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

            // ② HEADER/FOOTER 识别：页顶/页底区域内跨页重复文本与页码模式（先算，正文聚合时排除）
            Set<String> headers = HeaderFooterDetector.detectHeaders(pages, context);
            HeaderFooterDetector.FooterKeys footers = HeaderFooterDetector.detectFooters(pages, context);

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
            if (!Character.isWhitespace(c.codePoint())) {
                nonBlank++;
                if (TextUtil.isNonCommonChar(c.codePoint())) {
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
                lineY = c.y();
            } else if (Math.abs(c.y() - lineY) <= context.getProperties().getLineYTolerance()) {
                lineChars.add(c);
                lineY = (lineY + c.y()) / 2;
            } else {
                lines.add(buildLine(pageNo, lineChars));
                lineChars = new ArrayList<>();
                lineChars.add(c);
                lineY = c.y();
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

        return new PageContent(pageNo, pageWidth, pageHeight, metric, lines,
                RuleLines.collect(page, pageHeight));
    }

    /**
     * 行构建：行内 x 排序 → token 分词（字符间距超阈值断词）→ 目录行特征判定。
     */
    private PageLine buildLine(int pageNo, List<CharInfo> lineChars) {
        lineChars.sort(Comparator.comparingDouble(CharInfo::x));
        double x = lineChars.getFirst().x();
        BBox bounds = boundsOf(lineChars, CharInfo::x, CharInfo::y, CharInfo::width, CharInfo::height);
        double y = bounds.getY();
        double maxX = bounds.getX() + bounds.getWidth();
        double maxY = bounds.getY() + bounds.getHeight();
        double fontSize = lineChars.stream().mapToDouble(CharInfo::fontSize).average().orElse(0);
        CharInfo first = lineChars.getFirst();
        // token 分词：字符间距超阈值即断（空白字符不进入 token，自然形成词间断点）
        List<Token> tokens = new ArrayList<>();
        StringBuilder tokenText = new StringBuilder();
        double tokenStart = -1;
        double tokenEnd = 0;
        CharInfo prev = null;
        for (CharInfo c : lineChars) {
            if (Character.isWhitespace(c.codePoint())) {
                continue;
            }
            if (ObjectUtil.isNull(prev)) {
                tokenStart = c.x();
            }
            double gap = ObjectUtil.isNull(prev) ? 0 : c.x() - (prev.x() + prev.width());
            if (ObjectUtil.isNotNull(prev) && gap > TOKEN_GAP_RATIO * Math.max(c.fontSize(), 1)) {
                tokens.add(new Token(tokenText.toString(), tokenStart, tokenEnd));
                tokenText = new StringBuilder();
                tokenStart = c.x();
            }
            tokenText.appendCodePoint(c.codePoint());
            tokenEnd = c.x() + c.width();
            prev = c;
        }
        if (!tokenText.isEmpty()) {
            tokens.add(new Token(tokenText.toString(), tokenStart, tokenEnd));
        }
        String text = lineText(lineChars);
        boolean tocCandidate = TocLineFeature.isTocLine(text);
        return new PageLine(pageNo, x, y, maxX - x, maxY - y, text, fontSize,
                first.fontName(), first.bold(), tokens, List.copyOf(lineChars), tocCandidate);
    }

    /**
     * 行文本重建：按词边界补分隔符，内容流里的真实空白压缩为一个空格。
     * 词边界由 PDFBox 逐词回调给出（一次回调 = 一个词），两侧同为 CJK 时不补空格。
     */
    static String lineText(List<CharInfo> sortedByX) {
        StringBuilder text = new StringBuilder();
        CharInfo prev = null;
        for (CharInfo current : sortedByX) {
            if (Character.isWhitespace(current.codePoint())) {
                appendSpace(text);
            } else {
                if (ObjectUtil.isNotNull(prev) && prev.wordEnd() && current.wordStart()
                        && !(isCjk(prev.codePoint()) && isCjk(current.codePoint()))) {
                    appendSpace(text);
                }
                text.appendCodePoint(current.codePoint());
            }
            prev = current;
        }
        if (!text.isEmpty() && text.charAt(text.length() - 1) == ' ') {
            text.setLength(text.length() - 1);
        }
        return text.toString();
    }

    /** 行间分隔：任一侧取不到可见字符、或两侧同为 CJK 时不补空格，其余补一个空格 */
    static String lineSeparator(PageLine upper, PageLine lower) {
        return lineSeparator(upper.text(), lower.text());
    }

    /** 文本间分隔：口径同行间分隔（供格内多行拼接复用） */
    static String lineSeparator(String upperText, String lowerText) {
        int upperCodePoint = lastVisibleCodePoint(upperText);
        int lowerCodePoint = firstVisibleCodePoint(lowerText);
        if (upperCodePoint < 0 || lowerCodePoint < 0
                || (isCjk(upperCodePoint) && isCjk(lowerCodePoint))) {
            return "";
        }
        return " ";
    }

    /** CJK 码位：Han、假名、谚文、CJK 标点与符号、全角形式 */
    static boolean isCjk(int codePoint) {
        return (codePoint >= 0x3400 && codePoint <= 0x4DBF)
                || (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
                || (codePoint >= 0x3040 && codePoint <= 0x30FF)
                || (codePoint >= 0xAC00 && codePoint <= 0xD7AF)
                || (codePoint >= 0x1100 && codePoint <= 0x11FF)
                || (codePoint >= 0x3000 && codePoint <= 0x303F)
                || (codePoint >= 0xFF00 && codePoint <= 0xFFEF);
    }

    /** 追加一个空格：行首与连续空白都不追加 */
    private static void appendSpace(StringBuilder text) {
        if (!text.isEmpty() && text.charAt(text.length() - 1) != ' ') {
            text.append(' ');
        }
    }

    /** 首个非空白码位；取不到返回 -1 */
    private static int firstVisibleCodePoint(String text) {
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            if (!Character.isWhitespace(codePoint)) {
                return codePoint;
            }
            i += Character.charCount(codePoint);
        }
        return -1;
    }

    /** 末个非空白码位；取不到返回 -1 */
    private static int lastVisibleCodePoint(String text) {
        int result = -1;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            if (!Character.isWhitespace(codePoint)) {
                result = codePoint;
            }
            i += Character.charCount(codePoint);
        }
        return result;
    }

    /**
     * 页眉/页脚元素：文本 + 页码 + 包围盒 + 区域溯源（页首/页底装配口径一致，只差区域与元素类型）。
     *
     * @param id     元素 ID
     * @param type   HEADER / FOOTER
     * @param text   元素文本
     * @param area   区域溯源（页首区域 / 页底区域）
     * @param pageNo 页码
     * @param line   来源行（取包围盒）
     * @param fileId 文件 ID
     */
    private static ParseElement bandElement(String id, ElementType type, String text, String area,
                                            int pageNo, PageLine line, String fileId) {
        ParseElement element = ParseElement.of(id, type);
        element.setText(text);
        element.setPage(pageNo);
        element.setBbox(new BBox(line.x(), line.y(), line.width(), line.height()));
        element.setProvenance(new Provenance(fileId, area));
        return element;
    }

    /**
     * 单页元素组装：页眉/页脚元素（每页各一条） → 正文按 y 顺序推进（表格候选块与段落互斥结算）。
     */
    private void assemblePageElements(PageContent page, Set<String> headers,
                                      HeaderFooterDetector.FooterKeys footers,
                                      List<ParseElement> elements, ParseSource source, String fileId,
                                      ParseProperties properties) {
        double headerBandY = page.pageHeight() * properties.getHeaderAreaRatio();
        double footerBandY = page.pageHeight() * (1 - properties.getFooterAreaRatio());

        // 页首 HEADER 元素：本页页眉带内命中键的行各产一条（文本取原行）
        for (PageLine line : page.lines()) {
            String key = HeaderFooterDetector.normalizeKey(line.text());
            if (line.y() < headerBandY && headers.contains(key)) {
                elements.add(bandElement(
                        "h" + page.pageNo() + "_" + Integer.toHexString(key.hashCode()),
                        ElementType.HEADER, line.text().trim(), "pdf#top-area", page.pageNo(), line, fileId));
            }
        }

        // 页底 FOOTER 元素：本页页底带内命中页脚键或页码位置的行各产一条（文本取原行）
        for (PageLine line : page.lines()) {
            if (line.y() + line.height() < footerBandY) {
                continue;
            }
            String footerKey = HeaderFooterDetector.footerBase(HeaderFooterDetector.normalizeKey(line.text()));
            if (footers.matches(footerKey, line)) {
                elements.add(bandElement(
                        "f" + page.pageNo() + "_" + Integer.toHexString(footerKey.hashCode()),
                        ElementType.FOOTER, line.text().trim(), "pdf#bottom-area", page.pageNo(), line, fileId));
            }
        }

        // 正文：页眉带内的命中行与页底带内的页脚行不进正文（内容已由上面的元素承载）
        List<PageLine> bodyLines = page.lines().stream()
                .filter(line -> line.y() >= headerBandY
                        || !headers.contains(HeaderFooterDetector.normalizeKey(line.text())))
                .filter(line -> {
                    if (line.y() + line.height() < footerBandY) {
                        return true;
                    }
                    String footerKey = HeaderFooterDetector.footerBase(
                            HeaderFooterDetector.normalizeKey(line.text()));
                    return !footers.matches(footerKey, line);
                })
                .sorted(Comparator.comparingDouble(PageLine::y))
                .toList();

        // 线框区域：区域内行归线网格表格，其余行按段落与文本表格候选推进
        List<RuleLines.Region> regions = RuleLines.regions(page.segments(), properties.getTableLineCoverRatio());
        List<PageLine> run = new ArrayList<>();
        RuleLines.Region runRegion = null;
        for (PageLine line : bodyLines) {
            RuleLines.Region region = regionOf(regions, line);
            if (region != runRegion) {
                flushRun(run, runRegion, page, elements, source, fileId, properties);
                run = new ArrayList<>();
                runRegion = region;
            }
            run.add(line);
        }
        flushRun(run, runRegion, page, elements, source, fileId, properties);
    }

    /** 行归属的线框区域：行中心落在区域内即归属；无归属返回 null */
    private static RuleLines.Region regionOf(List<RuleLines.Region> regions, PageLine line) {
        double centerX = line.x() + line.width() / 2;
        double centerY = line.y() + line.height() / 2;
        for (RuleLines.Region region : regions) {
            if (region.contains(centerX, centerY)) {
                return region;
            }
        }
        return null;
    }

    /** 结算一段连续行：线框区域走线网格表格，其余行走段落与文本表格候选 */
    private void flushRun(List<PageLine> run, RuleLines.Region region, PageContent page,
                          List<ParseElement> elements, ParseSource source, String fileId,
                          ParseProperties properties) {
        if (run.isEmpty()) {
            return;
        }
        if (ObjectUtil.isNotNull(region)) {
            flushRuledTable(run, region, page, elements, source, fileId, properties);
            return;
        }
        flushTextRun(run, page, elements, source, fileId, properties);
    }

    /** 无边框路径：段落聚合 + 文本表格候选块（行距超阈值结算段落；候选行连续成块） */
    private void flushTextRun(List<PageLine> run, PageContent page, List<ParseElement> elements,
                              ParseSource source, String fileId, ParseProperties properties) {
        List<PageLine> block = new ArrayList<>();
        List<PageLine> paragraph = new ArrayList<>();
        PageLine prev = null;
        for (PageLine line : run) {
            if (ObjectUtil.isNotNull(prev)
                    && verticalGap(prev, line) > paragraphGapThreshold(prev, properties)) {
                // 行距超阈值：先结算段落；表格候选块允许更大行距（表格行距可达数倍行高），
                // 仅当新行不再具备宽列距（非表格候选）时才结算表格块
                if (!paragraph.isEmpty()) {
                    elements.add(toParagraphElement(paragraph, fileId));
                    paragraph = new ArrayList<>();
                }
                if (!block.isEmpty() && !TableCandidateDetector.isCandidate(line)) {
                    flushTableBlock(block, page, elements, source, fileId, properties);
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
                    flushTableBlock(block, page, elements, source, fileId, properties);
                    block = new ArrayList<>();
                }
                paragraph.add(line);
            }
            prev = line;
        }
        if (!block.isEmpty()) {
            flushTableBlock(block, page, elements, source, fileId, properties);
        }
        if (!paragraph.isEmpty()) {
            elements.add(toParagraphElement(paragraph, fileId));
        }
    }

    private double verticalGap(PageLine upper, PageLine lower) {
        return lower.y() - (upper.y() + upper.height());
    }

    private double paragraphGapThreshold(PageLine line, ParseProperties properties) {
        return line.fontSize() * properties.getParagraphGapRatio();
    }

    /**
     * 文本路径结算：列对齐聚类（支持度过滤）成表；列对齐失败或空白率过高时出 TABLE 事实并降级为段落
     */
    private void flushTableBlock(List<PageLine> block, PageContent page, List<ParseElement> elements,
                                 ParseSource source, String fileId, ParseProperties properties) {
        if (block.size() < 2) {
            elements.add(toParagraphElement(block, fileId));
            return;
        }
        List<List<Token>> tokenMatrix = block.stream().map(PageLine::tokens).toList();
        int minSupportRows = Math.max(2,
                (int) Math.ceil(block.size() * properties.getTableColumnSupportRatio()));
        List<Double> columnX = TableCandidateDetector.supportedColumns(tokenMatrix, minSupportRows);
        if (columnX.size() < TableCandidateDetector.MIN_TOKENS
                || !TableCandidateDetector.enoughLinesCoverColumns(tokenMatrix, columnX)) {
            degradeTable(block, page, elements, source, fileId, "列对齐聚类失败（疑似无边框/复杂表格）");
            return;
        }
        // 每行列边界：行左边界 + 内部列起点 + 行右边界（覆盖整行，列对齐外的字符不丢）
        List<List<Double>> rowBoundaries = new ArrayList<>();
        List<List<String>> rowTexts = new ArrayList<>();
        for (PageLine line : block) {
            List<Double> boundaries = new ArrayList<>();
            boundaries.add(line.x());
            for (int i = 1; i < columnX.size(); i++) {
                boundaries.add(columnX.get(i));
            }
            boundaries.add(line.x() + line.width());
            rowBoundaries.add(boundaries);
            rowTexts.add(cellTexts(line, boundaries));
        }
        if (blankTable(rowTexts, properties)) {
            degradeTable(block, page, elements, source, fileId, "空白率过高（疑似框线/表单区域）");
            return;
        }
        Integer headerRow = detectHeaderRow(rowTexts, block.stream().map(PageLine::fontSize).toList(),
                properties.getTableHeaderMaxNumericRatio());
        ParseElement table = ParseElement.of("t" + page.pageNo() + "_" + Integer.toHexString(block.hashCode()),
                ElementType.TABLE);
        table.setPage(page.pageNo());
        BBox bounds = boundsOf(block, PageLine::x, PageLine::y, PageLine::width, PageLine::height);
        table.setBbox(bounds);
        table.setRows(block.size());
        table.setCols(columnX.size());
        table.setHeaderRow(headerRow);
        table.setCutAtPageBottom(bounds.getY() + bounds.getHeight()
                >= page.pageHeight() * PAGE_BOTTOM_RATIO);
        List<ParseElement> cells = new ArrayList<>();
        for (int r = 0; r < block.size(); r++) {
            PageLine line = block.get(r);
            List<Double> boundaries = rowBoundaries.get(r);
            List<String> texts = rowTexts.get(r);
            for (int c = 0; c < columnX.size(); c++) {
                ParseElement cell = ParseElement.of("t" + page.pageNo() + "_" + r + "_" + c, ElementType.TABLE_CELL);
                cell.setText(texts.get(c));
                cell.setRow(r);
                cell.setCol(c);
                cell.setIsHeader(ObjectUtil.isNotNull(headerRow) && r == headerRow);
                // 单元格 bbox：列 x 范围 × 行带高度（组装环节续表列宽模式放宽规则输入）
                double cellX = boundaries.get(c);
                double cellWidth = boundaries.get(c + 1) - cellX;
                cell.setBbox(new BBox(cellX, line.y(), Math.max(cellWidth, 0), line.height()));
                cell.setProvenance(new Provenance(fileId, "pdf#page(" + page.pageNo() + ")/table[0]/cell[" + r + "," + c + "]"));
                cells.add(cell);
            }
        }
        table.setCells(cells);
        elements.add(table);
    }

    /** 线框路径结算：线网格定行列与合并跨度，格内文本按格矩形归属（行中心落带 + 字符落列区间） */
    private void flushRuledTable(List<PageLine> run, RuleLines.Region region, PageContent page,
                                 List<ParseElement> elements, ParseSource source, String fileId,
                                 ParseProperties properties) {
        RuleLines.Grid grid = RuleLines.fit(page.segments(), region, properties.getTableLineCoverRatio());
        if (ObjectUtil.isNull(grid)) {
            flushTextRun(run, page, elements, source, fileId, properties);
            return;
        }
        List<CellRect> rects = mergedCells(grid);
        Map<CellRect, String> texts = new LinkedHashMap<>();
        List<List<String>> rowTexts = new ArrayList<>();
        for (int r = 0; r < grid.rowCount(); r++) {
            rowTexts.add(new ArrayList<>());
        }
        for (CellRect rect : rects) {
            String text = ruledCellText(run, grid, rect);
            texts.put(rect, text);
            rowTexts.get(rect.row()).add(text);
        }
        if (blankTable(rowTexts, properties)) {
            degradeTable(run, page, elements, source, fileId, "空白率过高（疑似框线/表单区域）");
            return;
        }
        List<Double> rowFontSizes = new ArrayList<>();
        for (int r = 0; r < grid.rowCount(); r++) {
            double top = grid.rows().get(r);
            double bottom = grid.rows().get(r + 1);
            rowFontSizes.add(run.stream()
                    .filter(line -> inBand(line, top, bottom))
                    .mapToDouble(PageLine::fontSize).average().orElse(0));
        }
        Integer headerRow = detectHeaderRow(rowTexts, rowFontSizes,
                properties.getTableHeaderMaxNumericRatio());
        ParseElement table = ParseElement.of("t" + page.pageNo() + "_" + Integer.toHexString(run.hashCode()),
                ElementType.TABLE);
        table.setPage(page.pageNo());
        double left = grid.columns().getFirst();
        double right = grid.columns().getLast();
        double top = grid.rows().getFirst();
        double bottom = grid.rows().getLast();
        table.setBbox(new BBox(left, top, right - left, bottom - top));
        table.setRows(grid.rowCount());
        table.setCols(grid.colCount());
        table.setHeaderRow(headerRow);
        table.setCutAtPageBottom(bottom >= page.pageHeight() * PAGE_BOTTOM_RATIO);
        List<ParseElement> cells = new ArrayList<>();
        for (CellRect rect : rects) {
            ParseElement cell = ParseElement.of("t" + page.pageNo() + "_" + rect.row() + "_" + rect.col(),
                    ElementType.TABLE_CELL);
            cell.setText(texts.get(rect));
            cell.setRow(rect.row());
            cell.setCol(rect.col());
            if (rect.rowSpan() > 1) {
                cell.setRowSpan(rect.rowSpan());
            }
            if (rect.colSpan() > 1) {
                cell.setColSpan(rect.colSpan());
            }
            cell.setIsHeader(ObjectUtil.isNotNull(headerRow) && rect.row() == headerRow);
            double cellLeft = grid.columns().get(rect.col());
            double cellRight = grid.columns().get(rect.col() + rect.colSpan());
            double cellTop = grid.rows().get(rect.row());
            double cellBottom = grid.rows().get(rect.row() + rect.rowSpan());
            cell.setBbox(new BBox(cellLeft, cellTop, cellRight - cellLeft, cellBottom - cellTop));
            cell.setProvenance(new Provenance(fileId, "pdf#page(" + page.pageNo() + ")/table[0]/cell["
                    + rect.row() + "," + rect.col() + "]"));
            cells.add(cell);
        }
        table.setCells(cells);
        elements.add(table);
    }

    /** 合并格划分：向右按列分隔线缺失扩张，再向下按行分隔线缺失扩张 */
    static List<CellRect> mergedCells(RuleLines.Grid grid) {
        int rows = grid.rowCount();
        int cols = grid.colCount();
        boolean[][] used = new boolean[rows][cols];
        List<CellRect> rects = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (used[r][c]) {
                    continue;
                }
                int colSpan = 1;
                while (c + colSpan < cols
                        && Boolean.TRUE.equals(grid.columnGaps().get(r).get(c + colSpan - 1))) {
                    colSpan++;
                }
                int rowSpan = 1;
                while (r + rowSpan < rows && rowGapOpen(grid, r + rowSpan - 1, c, colSpan)) {
                    rowSpan++;
                }
                for (int rr = r; rr < r + rowSpan; rr++) {
                    for (int cc = c; cc < c + colSpan; cc++) {
                        used[rr][cc] = true;
                    }
                }
                rects.add(new CellRect(r, c, rowSpan, colSpan));
            }
        }
        return rects;
    }

    /** 行分隔线在给定列区间内是否全部缺失（缺失即纵向可合并） */
    private static boolean rowGapOpen(RuleLines.Grid grid, int gapIndex, int col, int colSpan) {
        for (int c = col; c < col + colSpan; c++) {
            if (!Boolean.TRUE.equals(grid.rowGaps().get(gapIndex).get(c))) {
                return false;
            }
        }
        return true;
    }

    /** 格内文本：行中心落在格行带的文本行，其落在格列区间的字符重建为文本，多行按行间规则拼接 */
    private static String ruledCellText(List<PageLine> run, RuleLines.Grid grid, CellRect rect) {
        double left = grid.columns().get(rect.col());
        double right = grid.columns().get(rect.col() + rect.colSpan());
        double top = grid.rows().get(rect.row());
        double bottom = grid.rows().get(rect.row() + rect.rowSpan());
        StringBuilder text = new StringBuilder();
        String previous = null;
        for (PageLine line : run) {
            if (!inBand(line, top, bottom)) {
                continue;
            }
            String piece = lineText(line.chars().stream()
                    .filter(ch -> ch.x() >= left && ch.x() < right)
                    .toList());
            if (piece.isEmpty()) {
                continue;
            }
            if (ObjectUtil.isNotNull(previous)) {
                text.append(lineSeparator(previous, piece));
            }
            text.append(piece);
            previous = piece;
        }
        return text.toString();
    }

    /** 行中心是否落在带内 */
    private static boolean inBand(PageLine line, double top, double bottom) {
        double center = line.y() + line.height() / 2;
        return center >= top && center < bottom;
    }

    /** 一组盒式元素的包围盒：左/上取最小、右/下取最大（行与字符共用同一实现） */
    private static <T> BBox boundsOf(List<T> items, ToDoubleFunction<T> x, ToDoubleFunction<T> y,
                                     ToDoubleFunction<T> width, ToDoubleFunction<T> height) {
        double left = items.stream().mapToDouble(x).min().orElse(0);
        double top = items.stream().mapToDouble(y).min().orElse(0);
        double right = items.stream()
                .mapToDouble(item -> x.applyAsDouble(item) + width.applyAsDouble(item)).max().orElse(0);
        double bottom = items.stream()
                .mapToDouble(item -> y.applyAsDouble(item) + height.applyAsDouble(item)).max().orElse(0);
        return new BBox(left, top, right - left, bottom - top);
    }

    /** 行内按边界切格：格内字符重建为文本（边界覆盖整行） */
    private static List<String> cellTexts(PageLine line, List<Double> boundaries) {
        List<String> texts = new ArrayList<>();
        for (int i = 0; i + 1 < boundaries.size(); i++) {
            double from = boundaries.get(i);
            double to = boundaries.get(i + 1);
            texts.add(lineText(line.chars().stream()
                    .filter(ch -> ch.x() >= from && ch.x() < to)
                    .toList()));
        }
        return texts;
    }

    /** 假表门限：非空单元格占比低于阈值即判为空白网格（弃表） */
    private static boolean blankTable(List<List<String>> rowTexts, ParseProperties properties) {
        int total = 0;
        int filled = 0;
        for (List<String> row : rowTexts) {
            for (String text : row) {
                total++;
                if (StrUtil.isNotBlank(text)) {
                    filled++;
                }
            }
        }
        return total > 0 && (double) filled / total < properties.getTableMinFilledRatio();
    }

    /** 表头行判定：首行含数字占比不超上限，且表体含数字（或首行字号大于表体最大字号） */
    static Integer detectHeaderRow(List<List<String>> rowTexts, List<Double> rowFontSizes,
                                   double maxNumericRatio) {
        if (rowTexts.size() < 2) {
            return null;
        }
        if (numericRatio(rowTexts.getFirst()) > maxNumericRatio) {
            return null;
        }
        if (rowTexts.stream().skip(1).anyMatch(cells -> numericRatio(cells) > 0)) {
            return 0;
        }
        double firstSize = rowFontSizes.getFirst();
        double bodyMax = rowFontSizes.stream().skip(1).mapToDouble(Double::doubleValue).max().orElse(0);
        return firstSize > bodyMax * 1.05 ? 0 : null;
    }

    /** 行内单元格含数字的比例 */
    private static double numericRatio(List<String> cells) {
        if (cells.isEmpty()) {
            return 0;
        }
        long numeric = cells.stream()
                .filter(text -> StrUtil.isNotBlank(text) && text.matches(".*\\d.*"))
                .count();
        return (double) numeric / cells.size();
    }

    /** 表格规则失败：出 TABLE 事实并把区域降级为段落 */
    private void degradeTable(List<PageLine> block, PageContent page, List<ParseElement> elements,
                              ParseSource source, String fileId, String evidence) {
        ParseFact fact = new ParseFact();
        fact.setType(SignalType.TABLE.name());
        fact.setRegion("page " + page.pageNo());
        fact.setEvidence(evidence);
        source.getFacts().add(fact);
        elements.add(toParagraphElement(block, fileId));
    }

    /** 合并格矩形：起始行列 + 跨度 */
    record CellRect(int row, int col, int rowSpan, int colSpan) {
    }

    /**
     * 段落元素组装：多行聚合 + 字体事实（取首行）+ 目录行特征。
     */
    private ParseElement toParagraphElement(List<PageLine> lines, String fileId) {
        PageLine first = lines.getFirst();
        PageLine last = lines.getLast();
        ParseElement element = ParseElement.of("p" + first.page() + "_" + lines.hashCode(), ElementType.PARAGRAPH);
        StringBuilder paragraph = new StringBuilder();
        PageLine prevLine = null;
        for (PageLine line : lines) {
            if (ObjectUtil.isNotNull(prevLine)) {
                paragraph.append(lineSeparator(prevLine, line));
            }
            paragraph.append(line.text());
            prevLine = line;
        }
        element.setText(paragraph.toString());
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

        /** 逐词回调：收集本次回调的全部码点，首末码点标记词边界 */
        @Override
        protected void writeString(String text, List<TextPosition> textPositions) {
            List<TextPosition> positions = textPositions.stream()
                    .filter(tp -> ObjectUtil.isNotNull(tp.getUnicode()))
                    .toList();
            int total = positions.stream()
                    .mapToInt(tp -> tp.getUnicode().codePointCount(0, tp.getUnicode().length()))
                    .sum();
            int index = 0;
            for (TextPosition tp : positions) {
                String unicode = tp.getUnicode();
                boolean bold = tp.getFont().getName().toLowerCase(Locale.ROOT).contains("bold");
                for (int i = 0; i < unicode.length(); ) {
                    int cp = unicode.codePointAt(i);
                    chars.add(new CharInfo(tp.getXDirAdj(), tp.getYDirAdj(), tp.getWidthDirAdj(),
                            tp.getHeightDir(), tp.getFontSizeInPt(), tp.getFont().getName(), bold, cp,
                            index == 0, index == total - 1));
                    index++;
                    i += Character.charCount(cp);
                }
            }
        }
    }
}
