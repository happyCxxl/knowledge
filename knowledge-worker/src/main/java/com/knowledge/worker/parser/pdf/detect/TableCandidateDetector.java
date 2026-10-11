package com.knowledge.worker.parser.pdf.detect;


import com.knowledge.common.utils.NullUtil;
import com.knowledge.worker.parser.pdf.model.PageLine;
import com.knowledge.worker.parser.pdf.model.Token;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 表格候选检测（package-private，PdfBoxDocumentParser 专用助手）：
 * 行级"宽列距"启发式判候选 → 列 x 聚类 → 覆盖度校验。
 * 列聚类容差按字号折算、由入参给出（大字号行的 token 起点抖动更大），不写固定像素值。
 *
 * @author cxxl
 */
public final class TableCandidateDetector {

    /** 表格候选：单行 token 数下限 */
    public static final int MIN_TOKENS = 2;

    private TableCandidateDetector() {
    }

    /** 表格候选：多 token 且存在"宽列距"（普通正文单词间距窄，表格列距宽） */
    public static boolean isCandidate(PageLine line) {
        if (line.tokens().size() < MIN_TOKENS) {
            return false;
        }
        double wideGap = Math.max(2.0 * Math.max(line.fontSize(), 1), 6.0);
        for (int i = 1; i < line.tokens().size(); i++) {
            double gap = line.tokens().get(i).xStart() - line.tokens().get(i - 1).xEnd();
            if (gap > wideGap) {
                return true;
            }
        }
        return false;
    }

    /** 列聚类：所有 token 起点按容差合并为列 x 列表（升序）。 */
    public static List<Double> clusterColumns(List<List<Token>> tokenMatrix, double tolerance) {
        List<Double> starts = new ArrayList<>();
        for (List<Token> line : tokenMatrix) {
            for (Token token : line) {
                starts.add(token.xStart());
            }
        }
        starts.sort(Double::compare);
        List<Double> clusters = new ArrayList<>();
        double current = -1;
        for (double s : starts) {
            if (current < 0 || s - current > tolerance) {
                clusters.add(s);
                current = s;
            } else {
                current = (current + s) / 2;
            }
        }
        return clusters;
    }

    /**
     * 支持度过滤的列聚类：簇内需有足够行数的 token 落位才保留该列，
     * 剔除个别行多出的 token 造成的虚列。
     *
     * @param tokenMatrix   块内各行 token
     * @param minSupportRows 列被保留所需的最少行数
     * @param tolerance     起点合并容差（按字号折算后传入）
     * @return 保留的列起点（升序）
     */
    public static List<Double> supportedColumns(List<List<Token>> tokenMatrix, int minSupportRows,
                                                double tolerance) {
        List<Double> kept = new ArrayList<>();
        for (double column : clusterColumns(tokenMatrix, tolerance)) {
            int support = 0;
            for (List<Token> line : tokenMatrix) {
                if (NullUtil.isNotNull(findTokenInColumn(line, column, tolerance))) {
                    support++;
                }
            }
            if (support >= minSupportRows) {
                kept.add(column);
            }
        }
        return kept;
    }

    /** 表格置信校验：至少 2 行完整覆盖全部列，且覆盖行数 ≥ 60%（不足则判规则失败降级）。 */
    public static boolean enoughLinesCoverColumns(List<List<Token>> tokenMatrix, List<Double> columns,
                                                  double tolerance) {
        int covered = 0;
        for (List<Token> line : tokenMatrix) {
            boolean covers = true;
            for (double col : columns) {
                if (NullUtil.isNull(findTokenInColumn(line, col, tolerance))) {
                    covers = false;
                    break;
                }
            }
            if (covers) {
                covered++;
            }
        }
        return covered >= 2 && covered >= tokenMatrix.size() * 0.6;
    }

    /** 取列起点与目标 x 对齐（容差内）的首个 token。 */
    public static Token findTokenInColumn(List<Token> tokens, double columnX, double tolerance) {
        Token best = null;
        for (Token token : tokens) {
            if (Math.abs(token.xStart() - columnX) <= tolerance) {
                if (NullUtil.isNull(best)) {
                    best = token;
                }
            }
        }
        return best;
    }
}
