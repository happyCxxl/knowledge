package com.knowledge.worker.parser.pdf.extract;

import com.knowledge.common.domain.parse.BBox;
import com.knowledge.common.domain.parse.signal.PageMetric;
import com.knowledge.common.utils.NullUtil;
import com.knowledge.common.utils.TextUtil;
import com.knowledge.worker.parser.ParseContext;
import com.knowledge.worker.parser.ParseProperties;
import com.knowledge.worker.parser.pdf.detect.RuleLines;
import com.knowledge.worker.parser.pdf.detect.TableCandidateDetector;
import com.knowledge.worker.parser.pdf.layout.PageLayoutAnalyzer;
import com.knowledge.worker.parser.pdf.model.CharInfo;
import com.knowledge.worker.parser.pdf.model.PageContent;
import com.knowledge.worker.parser.pdf.model.PageLine;
import com.knowledge.worker.parser.pdf.model.PdfPageGeometry;
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
        // 版面尺寸取裁剪框（未设裁剪框时 PDFBox 回落到媒体框）：与渲染出来的可见页一致，
        // 页眉页脚带比例、贴页底判定都按它算；产出元素时再按页几何换算到视口坐标
        PdfPageGeometry geometry = PdfPageGeometry.of(page);
        double pageWidth = geometry.textWidth();
        double pageHeight = geometry.textHeight();

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

        // 行聚合（y 容差聚类 → 表格行带整行保留、其余按栏沟切行 → 行内按 x 排序 → token 分词）
        List<CharInfo> sorted = stripper.chars.stream()
                .sorted(Comparator.comparingDouble(CharInfo::y).thenComparingDouble(CharInfo::x))
                .toList();
        List<Double> gutters = detectGutters(stripper.chars, pageWidth, pageHeight,
                PageLayoutAnalyzer.Options.of(context.getProperties()));
        LineTexts.Options lineTextOptions = LineTexts.Options.of(context.getProperties());
        List<List<CharInfo>> bands = groupBands(sorted, context.getProperties().getLineYTolerance());
        double alignmentTolerance = alignmentTolerance(bands, context.getProperties());
        boolean[] tableBands = tableBands(bands, context.getProperties(), alignmentTolerance);
        List<PageLine> rawLines = new ArrayList<>();
        for (int i = 0; i < bands.size(); i++) {
            if (tableBands[i]) {
                rawLines.add(buildLine(pageNo, bands.get(i), lineTextOptions));
            } else {
                rawLines.addAll(buildLines(pageNo, bands.get(i), gutters, lineTextOptions));
            }
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

        return new PageContent(pageNo, pageWidth, pageHeight, geometry, metric, lines, graphics.segments(),
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
    private List<PageLine> buildLines(int pageNo, List<CharInfo> lineChars, List<Double> gutters,
                                      LineTexts.Options lineTextOptions) {
        if (gutters.isEmpty()
                || lineChars.stream().anyMatch(c -> PageLayoutAnalyzer.crossesAny(c.x(), c.width(), gutters))) {
            return List.of(buildLine(pageNo, lineChars, lineTextOptions));
        }
        Map<Integer, List<CharInfo>> byColumn = new LinkedHashMap<>();
        for (CharInfo c : lineChars) {
            byColumn.computeIfAbsent(PageLayoutAnalyzer.columnOf(c.x(), c.width(), gutters),
                    k -> new ArrayList<>()).add(c);
        }
        return byColumn.values().stream().map(group -> buildLine(pageNo, group, lineTextOptions)).toList();
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

    /** 行带：同一 y 容差内的字符（按栏切分前的一整行） */
    private static List<List<CharInfo>> groupBands(List<CharInfo> sorted, double yTolerance) {
        List<List<CharInfo>> bands = new ArrayList<>();
        List<CharInfo> band = new ArrayList<>();
        double bandY = -1;
        for (CharInfo c : sorted) {
            if (band.isEmpty()) {
                band.add(c);
                bandY = c.y();
            } else if (Math.abs(c.y() - bandY) <= yTolerance) {
                band.add(c);
                bandY = (bandY + c.y()) / 2;
            } else {
                bands.add(band);
                band = new ArrayList<>();
                band.add(c);
                bandY = c.y();
            }
        }
        if (!band.isEmpty()) {
            bands.add(band);
        }
        return bands;
    }

    /** 对齐容差：按全页最大字号折算（与列聚类同一口径） */
    private static double alignmentTolerance(List<List<CharInfo>> bands, ParseProperties properties) {
        double maxFontSize = bands.stream().flatMap(List::stream)
                .mapToDouble(CharInfo::fontSize).max().orElse(0);
        return properties.getTableColumnToleranceRatio() * Math.max(maxFontSize, 1);
    }

    /**
     * 全页对齐位置：所有行带 token 的左起点与右边界聚类，保留被足够行带支持的位置。
     * 数字列右对齐时左起点逐行不同、右边界一致，两侧都取才认得出这一列。
     */
    private static List<Double> alignedPositions(List<List<CharInfo>> bands, double tolerance, int minRows) {
        List<Position> positions = new ArrayList<>();
        for (int index = 0; index < bands.size(); index++) {
            for (Token token : tokens(bands.get(index))) {
                positions.add(new Position(token.xStart(), index));
                positions.add(new Position(token.xEnd(), index));
            }
        }
        positions.sort(Comparator.comparingDouble(Position::value));
        List<Double> aligned = new ArrayList<>();
        List<Position> cluster = new ArrayList<>();
        double center = 0;
        for (Position position : positions) {
            if (!cluster.isEmpty() && Math.abs(position.value() - center) > tolerance) {
                addAligned(aligned, cluster, minRows);
                cluster = new ArrayList<>();
            }
            center = cluster.isEmpty() ? position.value()
                    : (center * cluster.size() + position.value()) / (cluster.size() + 1);
            cluster.add(position);
        }
        addAligned(aligned, cluster, minRows);
        return aligned;
    }

    /** 位置簇收口：簇内来源行带数达到下限即算一个对齐位置 */
    private static void addAligned(List<Double> aligned, List<Position> cluster, int minRows) {
        if (cluster.stream().map(Position::band).distinct().count() >= minRows) {
            aligned.add(cluster.stream().mapToDouble(Position::value).average().orElse(0));
        }
    }

    /**
     * 表格行带标记：先找「锚行」（行内落在 ≥3 个对齐位置上的 token 数达标），
     * 再把**锚行之间**、token 全部落在锚行 x 范围内的行带并入 —— 单元格内换行的续行与
     * 空单元格的稀疏行由此保住整行，不再被栏沟撕成两行。
     * 只有 2 列对齐时与双栏正文无法区分（双栏正文必须保持栏内阅读顺序），故锚行下限取 3。
     */
    private static boolean[] tableBands(List<List<CharInfo>> bands, ParseProperties properties, double tolerance) {
        List<List<Token>> tokensPerBand = bands.stream().map(PdfPageExtractor::tokens).toList();
        boolean[] result = new boolean[bands.size()];
        List<Double> alignedPositions = alignedPositions(bands, tolerance, properties.getTableAlignmentMinRows());
        if (alignedPositions.isEmpty()) {
            return result;
        }
        List<Integer> anchors = new ArrayList<>();
        for (int i = 0; i < bands.size(); i++) {
            if (matchedPositions(tokensPerBand.get(i), alignedPositions, tolerance)
                    >= properties.getTableRowMinColumns()) {
                anchors.add(i);
            }
        }
        if (anchors.size() < 2) {
            return result;
        }
        // 表格区域：锚行区间 + 两侧紧邻的合格行带（行距不超过锚行间距）
        int first = anchors.getFirst();
        int last = anchors.getLast();
        double left = bandLeft(tokensPerBand, first, last);
        double right = bandRight(tokensPerBand, first, last);
        double spacing = anchorSpacing(bands, anchors);
        while (first > 0 && bandY(bands, first) - bandY(bands, first - 1) <= spacing
                && qualifies(tokensPerBand.get(first - 1), left, right)) {
            first--;
        }
        while (last + 1 < bands.size() && bandY(bands, last + 1) - bandY(bands, last) <= spacing
                && qualifies(tokensPerBand.get(last + 1), left, right)) {
            last++;
        }
        for (int i = first; i <= last; i++) {
            result[i] = anchors.contains(i) || qualifies(tokensPerBand.get(i), left, right);
        }
        return result;
    }

    /** 区域内合格行带：token 数达下限且全部落在区域 x 范围内 */
    private static boolean qualifies(List<Token> tokens, double left, double right) {
        return tokens.size() >= TableCandidateDetector.MIN_TOKENS
                && tokens.stream().allMatch(token -> token.xStart() >= left && token.xEnd() <= right);
    }

    private static double bandLeft(List<List<Token>> tokensPerBand, int first, int last) {
        return tokensPerBand.subList(first, last + 1).stream().flatMap(List::stream)
                .mapToDouble(Token::xStart).min().orElse(0);
    }

    private static double bandRight(List<List<Token>> tokensPerBand, int first, int last) {
        return tokensPerBand.subList(first, last + 1).stream().flatMap(List::stream)
                .mapToDouble(Token::xEnd).max().orElse(0);
    }

    /** 锚行间距：相邻锚行 y 差的平均值（区域扩展与续行归并的纵向尺度） */
    private static double anchorSpacing(List<List<CharInfo>> bands, List<Integer> anchors) {
        double sum = 0;
        for (int i = 1; i < anchors.size(); i++) {
            sum += bandY(bands, anchors.get(i)) - bandY(bands, anchors.get(i - 1));
        }
        return anchors.size() > 1 ? sum / (anchors.size() - 1) : 0;
    }

    private static double bandY(List<List<CharInfo>> bands, int index) {
        return bands.get(index).getFirst().y();
    }

    /** 行内落在全页对齐位置（左起点或右边界）上的 token 数 */
    private static long matchedPositions(List<Token> tokens, List<Double> alignedPositions, double tolerance) {
        return tokens.stream()
                .filter(token -> aligns(token.xStart(), alignedPositions, tolerance)
                        || aligns(token.xEnd(), alignedPositions, tolerance))
                .count();
    }

    private static boolean aligns(double value, List<Double> positions, double tolerance) {
        return positions.stream().anyMatch(position -> Math.abs(position - value) <= tolerance);
    }

    /** 对齐位置候选：token 左起点 / 右边界 + 来源行带序号（支持度按行带去重） */
    private record Position(double value, int band) {
    }

    /** 行内 token 分词：字符间距超阈值即断（空白字符不进入 token，自然形成词间断点） */
    private static List<Token> tokens(List<CharInfo> lineChars) {
        List<CharInfo> sorted = lineChars.stream()
                .sorted(Comparator.comparingDouble(CharInfo::x))
                .toList();
        List<Token> tokens = new ArrayList<>();
        StringBuilder tokenText = new StringBuilder();
        double tokenStart = -1;
        double tokenEnd = 0;
        CharInfo prev = null;
        for (CharInfo c : sorted) {
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
        return tokens;
    }

    /** 行构建：行内 x 排序 → token 分词（字符间距超阈值断词）→ 目录行特征判定 */
    private PageLine buildLine(int pageNo, List<CharInfo> lineChars, LineTexts.Options lineTextOptions) {
        lineChars.sort(Comparator.comparingDouble(CharInfo::x));
        double x = lineChars.getFirst().x();
        BBox bounds = PdfGeometry.boundsOf(lineChars, CharInfo::x, CharInfo::y, CharInfo::width, CharInfo::height);
        double fontSize = lineChars.stream().mapToDouble(CharInfo::fontSize).average().orElse(0);
        CharInfo first = lineChars.getFirst();
        List<Token> tokens = tokens(lineChars);
        String text = LineTexts.lineText(lineChars, lineTextOptions);
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
            for (TextPosition tp : positions) {
                String unicode = tp.getUnicode();
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
}
