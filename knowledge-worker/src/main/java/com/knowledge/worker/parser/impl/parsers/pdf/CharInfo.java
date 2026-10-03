package com.knowledge.worker.parser.impl.parsers.pdf;

/**
 * PDF 字符事实：坐标与字体 + 码点 + 词边界标记（词边界来自 PDFBox 的逐词回调）。
 * 行文本重建与单元格文本归属共用本模型。
 *
 * @author cxxl
 */
public record CharInfo(double x, double y, double width, double height, double fontSize,
                       String fontName, boolean bold, int codePoint, boolean wordStart, boolean wordEnd) {
}
