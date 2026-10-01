package com.knowledge.worker.chunking.impl.body;

import cn.hutool.core.util.StrUtil;
import com.knowledge.common.domain.chunk.Chunk;
import com.knowledge.common.domain.preprocess.ViewElement;
import com.knowledge.common.enums.chunk.ChunkContentType;
import com.knowledge.common.enums.chunk.ChunkRoute;
import com.knowledge.worker.chunking.SliceContext;
import com.knowledge.worker.chunking.slice.SliceStrategy;
import com.knowledge.worker.chunking.strategy.ChunkRouteConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 正文聚合类切片器骨架：以 {@code SliceContext.bodyBuffer} 做跨元素缓冲。
 * 空文本空片、超长降级、累计到上限结算由本类处理，聚合粒度由子类实现。
 *
 * @author cxxl
 */
public abstract class AbstractBodySliceStrategy implements SliceStrategy {

    @Override
    public final List<Chunk> slice(ViewElement element, SliceContext context) {
        String text = element.getNormalizedText();
        if (StrUtil.isBlank(text)) {
            return List.of();
        }
        return sliceText(text, element, context);
    }

    @Override
    public final List<Chunk> flush(SliceContext context) {
        if (context.getBodyBuffer().isEmpty()) {
            return List.of();
        }
        return List.of(flushGroup(context));
    }

    /**
     * 切分单个非空元素的文本（正文缓冲的入队与结算由子类自行处理）。
     *
     * @param text    非空文本（元素检索文本口径）
     * @param element 视图元素
     * @param context 切片上下文（含策略路由、兜底切片器与正文缓冲）
     */
    protected abstract List<Chunk> sliceText(String text, ViewElement element, SliceContext context);

    /** 结算正文缓冲（调用前缓冲非空；结算后缓冲已清空） */
    protected abstract Chunk flushGroup(SliceContext context);

    /**
     * 超长降级：按策略所选兜底切片器切片，逐片标 FALLBACK 与降级原因。
     * 调用前须先结算既有缓冲（降级片不与聚合片混作一组）。
     *
     * @param text    超长文本（整元素或单句）
     * @param element 来源元素（取元素 ID 与页码）
     * @param context 切片上下文
     * @param reason  降级原因（写入 fallbackReason）
     */
    protected final List<Chunk> fallbackChunks(String text, ViewElement element, SliceContext context, String reason) {
        List<Chunk> chunks = new ArrayList<>();
        ChunkRouteConfig fallbackConfig = context.getStrategy().route(ChunkRoute.FALLBACK);
        for (String piece : context.getFallback().slice(text, fallbackConfig)) {
            Chunk chunk = BodyChunkSupport.buildChunk(ChunkContentType.FALLBACK.name(), piece, context,
                    List.of(element.getElementId()), BodyChunkSupport.pageRangeOf(element));
            chunk.setFallbackReason(reason);
            chunks.add(chunk);
        }
        return chunks;
    }
}
