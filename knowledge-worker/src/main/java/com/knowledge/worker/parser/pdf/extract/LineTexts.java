package com.knowledge.worker.parser.pdf.extract;

import com.knowledge.common.utils.NullUtil;
import com.knowledge.worker.parser.pdf.model.CharInfo;
import com.knowledge.worker.parser.pdf.model.PageLine;

import java.util.List;

/**
 * 行文本重建（无状态纯函数）：按 PDFBox 逐词回调给出的词边界补分隔符，
 * 内容流里的真实空白压缩为一个空格；两侧同为 CJK 时不补空格。
 *
 * @author cxxl
 */
public final class LineTexts {

    private LineTexts() {
    }

    /** 行文本：按词边界补分隔符，真实空白压成一个空格 */
    public static String lineText(List<CharInfo> sortedByX) {
        StringBuilder text = new StringBuilder();
        CharInfo prev = null;
        for (CharInfo current : sortedByX) {
            if (Character.isWhitespace(current.codePoint())) {
                appendSpace(text);
            } else {
                if (NullUtil.isNotNull(prev) && prev.wordEnd() && current.wordStart()
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
    public static String lineSeparator(PageLine upper, PageLine lower) {
        return lineSeparator(upper.text(), lower.text());
    }

    /** 文本间分隔：口径同行间分隔（供格内多行拼接复用） */
    public static String lineSeparator(String upperText, String lowerText) {
        int upperCodePoint = lastVisibleCodePoint(upperText);
        int lowerCodePoint = firstVisibleCodePoint(lowerText);
        if (upperCodePoint < 0 || lowerCodePoint < 0
                || (isCjk(upperCodePoint) && isCjk(lowerCodePoint))) {
            return "";
        }
        return " ";
    }

    /** CJK 码位：Han、假名、谚文、CJK 标点与符号、全角形式 */
    public static boolean isCjk(int codePoint) {
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
}
