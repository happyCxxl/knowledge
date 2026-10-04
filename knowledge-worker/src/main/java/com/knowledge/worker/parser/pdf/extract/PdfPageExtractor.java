package com.knowledge.worker.parser.pdf.extract;

import com.knowledge.common.domain.parse.BBox;
import com.knowledge.common.domain.parse.signal.PageMetric;
import com.knowledge.common.utils.NullUtil;
import com.knowledge.common.utils.TextUtil;
import com.knowledge.worker.parser.ParseContext;
import com.knowledge.worker.parser.pdf.detect.RuleLines;
import com.knowledge.worker.parser.pdf.layout.PageLayoutAnalyzer;
import com.knowledge.worker.parser.pdf.model.CharInfo;
import com.knowledge.worker.parser.pdf.model.PageContent;
import com.knowledge.worker.parser.pdf.model.PageLine;
import com.knowledge.worker.parser.pdf.model.Token;
import com.knowledge.worker.parser.support.TocLineFeature;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 单页提取：字符 + 坐标 + 字体 → 页级指标（字符数/乱码率/文字占比/图片面积）→
 * 行聚合（y 容差）→ 按栏沟切行 → 阅读顺序重排 → token 分词，产出 {@link PageContent}。
 *
 * @author cxxl
 */
public class PdfPageExtractor {

    /** token 分词的空隙阈值（字符 x 间距 ÷ 字号） */
    private static final double TOKEN_GAP_RATIO = 0.35;

    private final PageLayoutAnalyzer layoutAnalyzer;

    public PdfPageExtractor(PageLayoutAnalyzer layoutAnalyzer) {
        this.layoutAnalyzer = layoutAnalyzer;
    }

    /** 提取一页：字符级指标 → 行聚合 → 栏切分 → 阅读顺序 → 文字占比 */
    public PageContent extractPage(PDDocument doc, int pageNo, ParseContext context) throws IOException {
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
        metric.setGarbledRatio(nonBlank > 0 ? (double) nonCommon / nonBlank : 0);
        RuleLines.Graphics graphics = RuleLines.collect(page, pageHeight);
        metric.setImageAreaRatio(RuleLines.imageAreaRatio(graphics.imageBoxes(), pageWidth, pageHeight));

        // 行聚合（y 容差聚类 → 按栏沟切行 → 行内按 x 排序 → token 分词）
        List<CharInfo> sorted = stripper.chars.stream()
                .sorted(Comparator.comparingDouble(CharInfo::y).thenComparingDouble(CharInfo::x))
                .toList();
        List<Double> gutters = detectGutters(stripper.chars, pageWidth, pageHeight,
                PageLayoutAnalyzer.Options.of(context.getProperties()));
        List<PageLine> rawLines = new ArrayList<>();
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
                rawLines.addAll(buildLines(pageNo, lineChars, gutters));
                lineChars = new ArrayList<>();
                lineChars.add(c);
                lineY = c.y();
            }
        }
        if (!lineChars.isEmpty()) {
            rawLines.addAll(buildLines(pageNo, lineChars, gutters));
        }
        List<PageLine> lines = orderLines(rawLines, gutters, pageWidth, pageHeight);

        // 文字占比：行级 bbox 累计（行宽 × 行高，行高取 max(字符高, 字号×1.2)），
        // 字符级小盒子累计会低估占版面积、误伤稀疏页
        double textArea = 0;
        for (PageLine line : lines) {
            double lineHeight = Math.max(line.height(), line.fontSize() * 1.2);
            textArea += line.width() * lineHeight;
        }
        metric.setTextAreaRatio(pageWidth * pageHeight > 0 ? textArea / (pageWidth * pageHeight) : 0);

        return new PageContent(pageNo, pageWidth, pageHeight, metric, lines, graphics.segments(),
                graphics.imageBoxes());
    }

    /** 栏沟检测：字符级投影交给版面端口（返回空即单栏） */
    private List<Double> detectGutters(List<CharInfo> chars, double pageWidth, double pageHeight,
                                       PageLayoutAnalyzer.Options options) {
        List<PageLayoutAnalyzer.Box> boxes = chars.stream()
                .map(c -> new PageLayoutAnalyzer.Box(c.x(), c.y(), c.width(), c.height()))
                .toList();
        return layoutAnalyzer.gutters(new PageLayoutAnalyzer.Input(pageWidth, pageHeight, boxes), options);
    }

    /** 按栏沟切行：整组字符横跨栏沟（跨栏行）时出一整行，其余按栏分组各出一行 */
    private List<PageLine> buildLines(int pageNo, List<CharInfo> lineChars, List<Double> gutters) {
        if (gutters.isEmpty()
                || lineChars.stream().anyMatch(c -> PageLayoutAnalyzer.crossesAny(c.x(), c.width(), gutters))) {
            return List.of(buildLine(pageNo, lineChars));
        }
        Map<Integer, List<CharInfo>> byColumn = new LinkedHashMap<>();
        for (CharInfo c : lineChars) {
            byColumn.computeIfAbsent(PageLayoutAnalyzer.columnOf(c.x(), c.width(), gutters),
                    k -> new ArrayList<>()).add(c);
        }
        return byColumn.values().stream().map(group -> buildLine(pageNo, group)).toList();
    }

    /** 阅读顺序重排：跨栏行分带，带内按栏序与栏内纵向顺序（单栏页保持纵向顺序） */
    private List<PageLine> orderLines(List<PageLine> lines, List<Double> gutters,
                                      double pageWidth, double pageHeight) {
        if (gutters.isEmpty() || lines.size() < 2) {
            return lines;
        }
        List<PageLayoutAnalyzer.Box> boxes = lines.stream()
                .map(line -> new PageLayoutAnalyzer.Box(line.x(), line.y(), line.width(), line.height()))
                .toList();
        PageLayoutAnalyzer.Result result = layoutAnalyzer.order(
                new PageLayoutAnalyzer.Input(pageWidth, pageHeight, boxes), gutters);
        return result.placed().stream().map(placed -> lines.get(placed.index())).toList();
    }

    /** 行构建：行内 x 排序 → token 分词（字符间距超阈值断词）→ 目录行特征判定 */
    private PageLine buildLine(int pageNo, List<CharInfo> lineChars) {
        lineChars.sort(Comparator.comparingDouble(CharInfo::x));
        double x = lineChars.getFirst().x();
        BBox bounds = PdfGeometry.boundsOf(lineChars, CharInfo::x, CharInfo::y, CharInfo::width, CharInfo::height);
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
            if (NullUtil.isNull(prev)) {
                tokenStart = c.x();
            }
            double gap = NullUtil.isNull(prev) ? 0 : c.x() - (prev.x() + prev.width());
            if (NullUtil.isNotNull(prev) && gap > TOKEN_GAP_RATIO * Math.max(c.fontSize(), 1)) {
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
        String text = LineTexts.lineText(lineChars);
        boolean tocCandidate = TocLineFeature.isTocLine(text);
        return new PageLine(pageNo, x, bounds.getY(), bounds.getWidth(), bounds.getHeight(), text, fontSize,
                first.fontName(), first.bold(), tokens, List.copyOf(lineChars), tocCandidate);
    }

    /** 逐词回调收集字符事实（一次回调 = 一个词，首末码点标记词边界） */
    private static final class PositionStripper extends PDFTextStripper {

        private final List<CharInfo> chars = new ArrayList<>();

        private PositionStripper() {
            super();
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) {
            List<TextPosition> positions = textPositions.stream()
                    .filter(tp -> NullUtil.isNotNull(tp.getUnicode()))
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
