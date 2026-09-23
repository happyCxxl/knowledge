package com.knowledge.worker.chunking.impl.body;

import cn.hutool.core.util.StrUtil;
import com.knowledge.common.domain.chunk.Chunk;
import com.knowledge.common.enums.chunk.ChunkContentType;
import com.knowledge.common.domain.preprocess.ViewElement;
import com.knowledge.worker.chunking.SliceContext;
import com.knowledge.worker.chunking.slice.SliceStrategy;
import com.knowledge.common.enums.chunk.ChunkAlgorithm;
import com.knowledge.worker.chunking.strategy.ChunkParamKeys;
import com.knowledge.worker.chunking.strategy.ChunkRouteConfig;
import com.knowledge.common.enums.chunk.ChunkRoute;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 正文切片器（句子聚合）：跨元素按句聚合（句边界与兜底同口径），累计 ≥ targetMaxLen 结算；
 * 超长单句先结算缓冲再走兜底降级（FALLBACK）。每句以合成元素入正文缓冲（复用 SliceContext.bodyBuffer）。
 *
 * @author cxxl
 */
@Component
public class SentenceAggregateSliceStrategy implements SliceStrategy {

    @Override
    public ChunkAlgorithm algorithm() {
        return ChunkAlgorithm.BODY_SENTENCE_AGGREGATE;
    }

    @Override
    public List<Chunk> slice(ViewElement element, SliceContext context) {
        String text = element.getNormalizedText();
        if (StrUtil.isBlank(text)) {
            return List.of();
        }
        ChunkRouteConfig bodyConfig = context.getStrategy().route(ChunkRoute.BODY);
        int targetMaxLen = bodyConfig.intParam(ChunkParamKeys.TARGET_MAX_LEN, 800);
        int softMaxLen = bodyConfig.intParam(ChunkParamKeys.SOFT_MAX_LEN, 1000);
        List<Chunk> emitted = new ArrayList<>();

        for (String sentence : BodyChunkSupport.splitSentences(text)) {
            if (sentence.length() > softMaxLen) {
                if (!context.getBodyBuffer().isEmpty()) {
                    emitted.add(flushGroup(context));
                }
                ChunkRouteConfig fallbackConfig = context.getStrategy().route(ChunkRoute.FALLBACK);
                for (String piece : context.getFallback().slice(sentence, fallbackConfig)) {
                    Chunk chunk = BodyChunkSupport.buildChunk(ChunkContentType.FALLBACK.name(), piece, context,
                            List.of(element.getElementId()), BodyChunkSupport.pageRangeOf(element));
                    chunk.setFallbackReason("超长句子递归降级");
                    emitted.add(chunk);
                }
            } else {
                ViewElement synthetic = new ViewElement();
                synthetic.setElementId(element.getElementId());
                synthetic.setType(element.getType());
                synthetic.setPage(element.getPage());
                synthetic.setNormalizedText(sentence);
                context.getBodyBuffer().add(synthetic);
                if (BodyChunkSupport.bufferLength(context.getBodyBuffer()) >= targetMaxLen) {
                    emitted.add(flushGroup(context));
                }
            }
        }
        return emitted;
    }

    @Override
    public List<Chunk> flush(SliceContext context) {
        if (context.getBodyBuffer().isEmpty()) {
            return List.of();
        }
        return List.of(flushGroup(context));
    }

    private Chunk flushGroup(SliceContext context) {
        BodyChunkSupport.BodyBuffer settled = BodyChunkSupport.settleBuffer(context);
        return BodyChunkSupport.buildChunk(ChunkContentType.PARAGRAPH.name(), settled.text(), context,
                settled.elementIds(), settled.pages());
    }
}
