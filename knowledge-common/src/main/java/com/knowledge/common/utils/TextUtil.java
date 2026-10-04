package com.knowledge.common.utils;

import java.util.HashSet;
import java.util.Set;

/**
 * 文本工具：乱码率判定与 Jaccard 相似度的公共口径（解析/组装/预处理环节共用，避免各写一套）。
 *
 * @author cxxl
 */
public final class TextUtil {

    private TextUtil() {
    }

    /**
     * 是否乱码字符：只认**不可读**的码点 —— 替换字符（U+FFFD）、私用区、未分配码点、控制字符。
     * 全角标点与引号、假名、谚文、生僻字（CJK 扩展 A 及以上）、希腊/西里尔字母、数学符号、
     * 圈号与项目符号等一律算正常字符。
     * 已知盲区：字体缺 ToUnicode 时错位出的可打印 ASCII 垃圾码点合法，不在本判据覆盖内。
     */
    public static boolean isNonCommonChar(int codePoint) {
        if (codePoint == 0xFFFD) {
            return true;
        }
        if ((codePoint >= 0xE000 && codePoint <= 0xF8FF) || codePoint >= 0xF0000) {
            return true;
        }
        if (codePoint < 0x20 || (codePoint >= 0x7F && codePoint <= 0x9F)) {
            return true;
        }
        return !Character.isDefined(codePoint);
    }

    /**
     * 文本乱码率：非常用字符码点数 ÷ 非空白码点总数（无有效字符时返回 0）。
     */
    public static double garbledRatio(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int nonBlank = 0;
        int nonCommon = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (!Character.isWhitespace(cp)) {
                nonBlank++;
                if (isNonCommonChar(cp)) {
                    nonCommon++;
                }
            }
            i += Character.charCount(cp);
        }
        return nonBlank > 0 ? (double) nonCommon / nonBlank : 0;
    }

    /**
     * 字符集 Jaccard 相似度（忽略空白字符）：去重合并与续表表头判定的公共口径。
     * 两端均为空文本视为同一（返回 1）。
     */
    public static double jaccardCharSet(String a, String b) {
        if (a.isBlank() && b.isBlank()) {
            return 1;
        }
        if (a.isBlank() || b.isBlank()) {
            return 0;
        }
        Set<Character> setA = new HashSet<>();
        a.chars().filter(c -> !Character.isWhitespace(c)).forEach(c -> setA.add((char) c));
        Set<Character> setB = new HashSet<>();
        b.chars().filter(c -> !Character.isWhitespace(c)).forEach(c -> setB.add((char) c));
        return jaccardRatio(setA, setB);
    }

    /**
     * bigram 集合相似度（字符二元组 Jaccard）：重复页/重复段判定的公共口径。
     * 两端均为空集合视为不相似（返回 0）。
     */
    public static double jaccardBigram(String a, String b) {
        return jaccardSet(bigramSet(a), bigramSet(b));
    }

    /** 文本 → 字符二元组集合（含空文本 → 空集合） */
    public static Set<String> bigramSet(String text) {
        Set<String> set = new HashSet<>();
        for (int i = 0; i < text.length() - 1; i++) {
            set.add(text.substring(i, i + 2));
        }
        return set;
    }

    /** 集合 Jaccard 相似度（两端同为空集视为不相似，返回 0） */
    public static double jaccardSet(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }

    /** 集合 Jaccard 相似度（两端同为空集视为同一，返回 1） */
    private static double jaccardRatio(Set<?> a, Set<?> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1;
        }
        Set<Object> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<Object> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 1 : (double) intersection.size() / union.size();
    }
}
