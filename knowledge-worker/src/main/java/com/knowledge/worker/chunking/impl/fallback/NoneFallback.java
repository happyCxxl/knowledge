package com.knowledge.worker.chunking.impl.fallback;

import cn.hutool.core.util.StrUtil;
import com.knowledge.worker.chunking.slice.FallbackSlicer;
import com.knowledge.common.enums.chunk.ChunkAlgorithm;
import com.knowledge.worker.chunking.strategy.ChunkRouteConfig;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 不兜底切片：超长文本原样单片返回（配合长上下文模型场景；切片方标 FALLBACK + fallbackReason 便于评测识别）。
 *
 * @author cxxl
 */
@Component
public class NoneFallback implements FallbackSlicer {

    @Override
    public ChunkAlgorithm algorithm() {
        return ChunkAlgorithm.FALLBACK_NONE;
    }

    @Override
    public List<String> slice(String text, ChunkRouteConfig config) {
        return StrUtil.isBlank(text) ? List.of() : List.of(text);
    }
}
