package com.knowledge.worker.chunking.impl.fallback;

import cn.hutool.core.util.StrUtil;
import com.knowledge.worker.chunking.slice.FallbackSlicer;
import com.knowledge.worker.chunking.strategy.ChunkParamKeys;
import com.knowledge.worker.chunking.strategy.ChunkRouteConfig;

import java.util.List;

/**
 * 窗口类兜底切片器公共骨架：固定窗口与递归两路都从 fallback 路由读 len/overlap，空文本一律空片；
 * 子类只实现"给定 len/overlap 怎么切"。
 *
 * @author cxxl
 */
public abstract class AbstractFallbackSlicer implements FallbackSlicer {

    @Override
    public final List<String> slice(String text, ChunkRouteConfig config) {
        if (StrUtil.isBlank(text)) {
            return List.of();
        }
        return doSlice(text, config.intParam(ChunkParamKeys.LEN, 500), config.intParam(ChunkParamKeys.OVERLAP, 50));
    }

    /**
     * 切分非空文本。
     *
     * @param text    非空文本
     * @param len     窗口长度（字符，解析时已补默认）
     * @param overlap 重叠长度（字符，仅本路使用）
     */
    protected abstract List<String> doSlice(String text, int len, int overlap);
}
