package com.knowledge.worker.parser.impl.parsers.pdf;

import com.knowledge.common.domain.parse.signal.PageMetric;

import java.util.List;

/**
 * PDF 单页提取内容：页号/版面尺寸/页级指标/聚合行列表。
 *
 * @author cxxl
 */
public record PageContent(int pageNo, double pageWidth, double pageHeight, PageMetric metric,
                          List<PageLine> lines) {
}
