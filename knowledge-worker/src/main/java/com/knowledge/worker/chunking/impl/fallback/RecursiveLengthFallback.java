package com.knowledge.worker.chunking.impl.fallback;

import cn.hutool.core.util.StrUtil;
import com.knowledge.worker.chunking.slice.FallbackSlicer;
import com.knowledge.worker.chunking.WindowSlicer;
import com.knowledge.worker.chunking.impl.body.BodyChunkSupport;
import com.knowledge.common.enums.chunk.ChunkAlgorithm;
import com.knowledge.worker.chunking.strategy.ChunkParamKeys;
import com.knowledge.worker.chunking.strategy.ChunkRouteConfig;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 递归兜底切片（现状逻辑接口化）：句边界优先；单句仍超长按固定 len + 重叠 overlap 硬切。
 * 参数从 fallback 路由配置读取（len/overlap，解析时已补默认）。重叠只用于本路（结构切片不用重叠）。
 *
 * @author cxxl
 */
@Component
public class RecursiveLengthFallback implements FallbackSlicer {

    @Override
    public ChunkAlgorithm algorithm() {
        return ChunkAlgorithm.FALLBACK_RECURSIVE;
    }

    @Override
    public List<String> slice(String text, ChunkRouteConfig config) {
        if (StrUtil.isBlank(text)) {
            return List.of();
        }
        int len = config.intParam(ChunkParamKeys.LEN, 500);
        int overlap = config.intParam(ChunkParamKeys.OVERLAP, 50);
        List<String> pieces = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String sentence : BodyChunkSupport.splitSentences(text)) {
            if (!buffer.isEmpty() && buffer.length() + sentence.length() > len) {
                pieces.add(buffer.toString());
                buffer = new StringBuilder();
            }
            if (sentence.length() > len) {
                if (!buffer.isEmpty()) {
                    pieces.add(buffer.toString());
                    buffer = new StringBuilder();
                }
                pieces.addAll(WindowSlicer.slice(sentence, len, overlap));
            } else {
                buffer.append(sentence);
            }
        }
        if (!buffer.isEmpty()) {
            pieces.add(buffer.toString());
        }
        return pieces;
    }
}
