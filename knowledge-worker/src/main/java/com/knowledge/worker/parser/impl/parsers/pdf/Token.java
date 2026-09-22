package com.knowledge.worker.parser.impl.parsers.pdf;

/**
 * PDF 行内 token（词）：文本 + x 起止坐标（表格列对齐/文字占比口径共用）。
 *
 * @author cxxl
 */
public record Token(String text, double xStart, double xEnd) {
}
