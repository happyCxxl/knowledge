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
     * 是否非常用字符（CJK 统一表意区 + 扩展 A + 可打印 ASCII + 常用中文标点/全角符号之外）。
     * 引号类字符不在常用集内，乱码率口径待样本标定。
     */
    public static boolean isNonCommonChar(int codePoint) {
        if (codePoint >= 0x4E00 && codePoint <= 0x9FFF) {
            return false;
        }
        if (codePoint >= 0x3400 && codePoint <= 0x4DBF) {
            return false;
        }
        if (codePoint >= 0x20 && codePoint <= 0x7E) {
            return false;
        }
        return "，。、；：？！（）《》—…·【】￥%&".indexOf(codePoint) < 0;
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
