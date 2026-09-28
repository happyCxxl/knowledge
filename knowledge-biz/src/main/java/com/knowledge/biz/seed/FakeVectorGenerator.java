package com.knowledge.biz.seed;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * 模拟向量生成器：把一段文本确定性地映射成单位向量。
 *
 * <p><b>这是为"向量化模型未接入"准备的替身，不是真实语义向量。</b>
 * 它只保证三件事：
 * <ol>
 *   <li>**确定性**：同一文本恒得同一向量（可复现，重跑种子结果一致）；</li>
 *   <li>**单位长度**：做 L2 归一化，因为 COSINE 度量要求归一化
 *       （见 {@code EmbeddingModel.normalized()}）；</li>
 *   <li>**同文本同向量**：与账本复用（cacheHit）语义自洽 —— 同内容天然命中。</li>
 * </ol>
 *
 * <p><b>它不保证语义相似性</b>：内容相近的两段文本不会得到相近的向量，
 * 所以用向量通道检索出来的排序**没有语义意义**。要让检索有语义，必须接入真实
 * 向量模型（{@code ModelGatewayPort}）。这一点在种子的产出说明里会明确标注。
 *
 * <p>实现方式：以文本 sha256 为种子驱动 {@link java.util.Random} 生成分量，
 * 再做 L2 归一化。用 sha256 而不是文本 hashCode，是为了让"换一个字符就完全不同"
 * 且不受 JVM 实现差异影响。
 *
 * @author cxxl
 */
public final class FakeVectorGenerator {

    private FakeVectorGenerator() {
    }

    /**
     * 生成单位向量。
     *
     * @param text      输入文本（= 切片内容口径）
     * @param dimension 目标维度（必须与 {@code kb_embedding_set.dimension} 一致，
     *                  否则 Milvus 写入会因维度不匹配失败）
     * @return 长度为 dimension 的单位向量
     * @throws IllegalArgumentException 维度非正
     */
    public static List<Float> generate(String text, int dimension) {
        if (dimension <= 0) {
            throw new IllegalArgumentException("向量维度必须为正: " + dimension);
        }
        long seed = seedOf(text);
        java.util.Random random = new java.util.Random(seed);

        List<Float> vector = new ArrayList<>(dimension);
        double sumOfSquares = 0.0;
        for (int i = 0; i < dimension; i += 1) {
            // nextGaussian 而非 nextDouble：分量近似零均值，向量方向分布更均匀，
            // 不至于所有分量都集中在 [0,1) 导致归一化后方向高度相关
            double value = random.nextGaussian();
            vector.add((float) value);
            sumOfSquares += value * value;
        }

        double norm = Math.sqrt(sumOfSquares);
        if (norm == 0.0) {
            // 概率上不可能（1024 个高斯分量同时为 0），但零向量会让 COSINE 距离无定义，
            // 所以显式兜底成一个合法单位向量，而不是把 NaN 写进向量库
            List<Float> fallback = new ArrayList<>(dimension);
            for (int i = 0; i < dimension; i += 1) {
                fallback.add(i == 0 ? 1.0f : 0.0f);
            }
            return fallback;
        }
        for (int i = 0; i < dimension; i += 1) {
            vector.set(i, (float) (vector.get(i) / norm));
        }
        return vector;
    }

    /** 文本 → 稳定的 64 位种子（取 sha256 前 8 字节） */
    private static long seedOf(String text) {
        byte[] digest = sha256(text);
        long seed = 0L;
        for (int i = 0; i < 8; i += 1) {
            seed = (seed << 8) | (digest[i] & 0xFFL);
        }
        return seed;
    }

    /**
     * 文本 → sha256 hex（小写）。
     *
     * <p>与生产实现 {@code EmbedHashes.sha256Hex} **同算法**（sha256 over UTF-8，
     * `%02x` 小写十六进制）。种子必须用同一个指纹，否则账本复用（cacheHit）永远命不中 ——
     * 那一列本来就是复用键。
     *
     * @param text 输入文本
     * @return 64 位十六进制指纹
     */
    public static String hash(String text) {
        byte[] digest = sha256(text);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // JDK 必有 SHA-256；这里是防御性兜底，不吞异常语义
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
