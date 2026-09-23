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
 * 正文切片器（段落聚合，现状默认）：标题层级为边界、段落/列表为边界、超长递归降级。
 * 参数从策略 body 路由配置读取（targetMaxLen/softMaxLen，解析时已补默认）；
 * 超长降级走策略所选兜底切片器（context.getFallback()）。
 * 聚合缓冲放在 SliceContext（每次运行独立），切片器本身无状态、并发安全；标题不成片——章节栈由管线维护。
 *
 * @author cxxl
 */
@Component
public class ParagraphSliceStrategy implements SliceStrategy {

    @Override
    public ChunkAlgorithm algorithm() {
        return ChunkAlgorithm.BODY_PARAGRAPH_AGGREGATE;
    }

    @Override
    public List<Chunk> slice(ViewElement element, SliceContext context) {
        String text = element.getNormalizedText();
        if (StrUtil.isBlank(text)) {
            return List.of();
        }
        ChunkRouteConfig config = context.getStrategy().route(ChunkRoute.BODY);
        int softMaxLen = config.intParam(ChunkParamKeys.SOFT_MAX_LEN, 1000);
        int targetMaxLen = config.intParam(ChunkParamKeys.TARGET_MAX_LEN, 800);
        List<ViewElement> buffer = context.getBodyBuffer();
        List<Chunk> emitted = new ArrayList<>();

        // 单元素超软上限：先结算既有组，再递归降级（章节→句子→固定长度）
        if (text.length() > softMaxLen) {
            if (!buffer.isEmpty()) {
                emitted.add(flushGroup(buffer, context));
            }
            ChunkRouteConfig fallbackConfig = context.getStrategy().route(ChunkRoute.FALLBACK);
            for (String piece : context.getFallback().slice(text, fallbackConfig)) {
                Chunk chunk = BodyChunkSupport.buildChunk(ChunkContentType.FALLBACK.name(), piece, context,
                        List.of(element.getElementId()), BodyChunkSupport.pageRangeOf(element));
                chunk.setFallbackReason("超长段落递归降级");
                emitted.add(chunk);
            }
            return emitted;
        }

        // 段落聚合：累计至目标上限在元素边界结算
        buffer.add(element);
        if (BodyChunkSupport.bufferLength(buffer) >= targetMaxLen) {
            emitted.add(flushGroup(buffer, context));
        }
        return emitted;
    }

    @Override
    public List<Chunk> flush(SliceContext context) {
        if (context.getBodyBuffer().isEmpty()) {
            return List.of();
        }
        return List.of(flushGroup(context.getBodyBuffer(), context));
    }

    private Chunk flushGroup(List<ViewElement> group, SliceContext context) {
        List<String> texts = group.stream().map(ViewElement::getNormalizedText)
                .filter(StrUtil::isNotBlank).toList();
        String content = String.join("\n", texts);
        List<String> ids = group.stream().map(ViewElement::getElementId).toList();
        List<Integer> pages = BodyChunkSupport.mergePages(group);
        group.clear();
        return BodyChunkSupport.buildChunk(ChunkContentType.PARAGRAPH.name(), content, context, ids, pages);
    }
}
