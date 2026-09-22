package com.knowledge.worker.parser.impl.parsers.pdf;

import cn.hutool.core.util.ObjectUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * PDF 表格候选检测（package-private，PdfBoxDocumentParser 专用助手）：
 * 行级"宽列距"启发式判候选 → 列 x 聚类 → 覆盖度校验。
 * 一期简化：无边框/复杂表格聚类失败时出 TABLE 事实，由管线降级为段落 + 告警。
 *
 * @author cxxl
 */
public final class TableCandidateDetector {

    /** 表格候选：单行 token 数下限 */
    public static final int MIN_TOKENS = 2;

    /** 列聚类容差（pt） */
    public static final double COLUMN_TOLERANCE = 3.0;

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
    public static List<Double> clusterColumns(List<List<Token>> tokenMatrix) {
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
            if (current < 0 || s - current > COLUMN_TOLERANCE) {
                clusters.add(s);
                current = s;
            } else {
                current = (current + s) / 2;
            }
        }
        return clusters;
    }

    /** 表格置信校验：至少 2 行完整覆盖全部列，且覆盖行数 ≥ 60%（不足则判规则失败降级）。 */
    public static boolean enoughLinesCoverColumns(List<List<Token>> tokenMatrix, List<Double> columns) {
        int covered = 0;
        for (List<Token> line : tokenMatrix) {
            boolean covers = true;
            for (double col : columns) {
                if (ObjectUtil.isNull(findTokenInColumn(line, col))) {
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
    public static Token findTokenInColumn(List<Token> tokens, double columnX) {
        Token best = null;
        for (Token token : tokens) {
            if (Math.abs(token.xStart() - columnX) <= COLUMN_TOLERANCE) {
                if (ObjectUtil.isNull(best)) {
                    best = token;
                }
            }
        }
        return best;
    }
}
