package com.knowledge.worker.chunking.impl;

import com.knowledge.common.enums.chunk.ChunkAlgorithm;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 算法注册表（工厂，泛型收口）：按 ChunkAlgorithm 建索引；
 * 归属校验（谓词）+ 重复注册报错 + 查找失败报错一次声明，切片器与兜底切片器两个注册表共用。
 * 新增算法 = 新增一个 @Component 实现，自动收录（开闭原则）；查找不到明确报错，不静默回退。
 *
 * @param <T> 注册的算法实现类型（SliceStrategy / FallbackSlicer）
 * @author cxxl
 */
public final class AlgorithmRegistry<T> {

    private final Map<ChunkAlgorithm, T> byAlgorithm = new HashMap<>();
    private final String kindLabel;

    /**
     * 建索引（启动期执行）：候选逐个校验归属并登记，重复/归属不符即启动失败。
     *
     * @param candidates  Spring 注入候选
     * @param algorithmOf 候选 → 算法提取
     * @param kindLabel   注册表种类名（报错文案前缀："切片器"/"兜底切片器"）
     * @param allowed     归属校验（不符即启动失败）
     */
    public AlgorithmRegistry(List<T> candidates, Function<T, ChunkAlgorithm> algorithmOf,
                             String kindLabel, Predicate<T> allowed) {
        this.kindLabel = kindLabel;
        for (T candidate : candidates) {
            ChunkAlgorithm algorithm = algorithmOf.apply(candidate);
            if (!allowed.test(candidate)) {
                throw new IllegalStateException(kindLabel + "归属校验失败（算法不应注册到本表）: algorithm="
                        + algorithm.key());
            }
            T existed = byAlgorithm.put(algorithm, candidate);
            if (existed != null) {
                throw new IllegalStateException(kindLabel + "重复注册: algorithm=" + algorithm.key());
            }
        }
    }

    /** 按算法取实现；未注册明确报错（SYSTEM_ERROR），不静默回退 */
    public T get(ChunkAlgorithm algorithm) {
        T candidate = algorithm == null ? null : byAlgorithm.get(algorithm);
        if (candidate == null) {
            throw new KnowledgeException(ErrorCode.SYSTEM_ERROR,
                    kindLabel + "无实现: algorithm=" + (algorithm == null ? "null" : algorithm.key()));
        }
        return candidate;
    }
}
