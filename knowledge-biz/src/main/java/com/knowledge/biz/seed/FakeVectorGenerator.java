package com.knowledge.biz.seed;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * 模拟向量生成器：把一段文本确定性地映射成单位向量。
 *
 * <p><b>向量化模型未接入时的替身，不是真实语义向量。</b>只保证三件事：
 * <ol>
 *   <li>**确定性**：同一文本恒得同一向量（可复现，重跑种子结果一致）；</li>
 *   <li>**单位长度**：L2 归一化后的单位向量（对应 COSINE 度量，见 {@code EmbeddingModel.normalized()}）；</li>
 *   <li>**同文本同向量**：同内容天然命中账本复用（cacheHit）语义。</li>
 * </ol>
 *
 * <p><b>不保证语义相似性</b>：内容相近的两段文本得不到相近的向量，向量通道的排序无语义意义；
 * 语义检索须接入真实向量模型（{@code ModelGatewayPort}）。
 *
 * <p>实现：以文本 sha256 为种子驱动 {@link java.util.Random} 生成分量后 L2 归一化；
 * sha256 保证换一个字符即完全不同，且不受 JVM 实现差异影响。
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
     * @param dimension 目标维度（必须与 {@code kb_embedding_set.dimension} 一致）
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
            // 显式兜底成一个合法单位向量，而不是把 NaN 写进向量库
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
     * <p>与生产实现 {@code EmbedHashes.sha256Hex} 同算法（sha256 over UTF-8，`%02x` 小写十六进制）：
     * 指纹不一致时账本复用（cacheHit）命不中。
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
