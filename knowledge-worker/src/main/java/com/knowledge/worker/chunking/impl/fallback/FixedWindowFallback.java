package com.knowledge.worker.chunking.impl.fallback;

import cn.hutool.core.util.StrUtil;
import com.knowledge.worker.chunking.slice.FallbackSlicer;
import com.knowledge.worker.chunking.WindowSlicer;
import com.knowledge.common.enums.chunk.ChunkAlgorithm;
import com.knowledge.worker.chunking.strategy.ChunkParamKeys;
import com.knowledge.worker.chunking.strategy.ChunkRouteConfig;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 固定窗口兜底切片：忽略语义边界，纯窗口 len + 重叠 overlap 硬切（评测基线口径）。
 *
 * @author cxxl
 */
@Component
public class FixedWindowFallback implements FallbackSlicer {

    @Override
    public ChunkAlgorithm algorithm() {
        return ChunkAlgorithm.FALLBACK_FIXED_WINDOW;
    }

    @Override
    public List<String> slice(String text, ChunkRouteConfig config) {
        if (StrUtil.isBlank(text)) {
            return List.of();
        }
        int len = config.intParam(ChunkParamKeys.LEN, 500);
        int overlap = config.intParam(ChunkParamKeys.OVERLAP, 50);
        return WindowSlicer.slice(text, len, overlap);
    }
}
