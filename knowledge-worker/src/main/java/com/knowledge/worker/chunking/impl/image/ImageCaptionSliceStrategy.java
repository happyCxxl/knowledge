package com.knowledge.worker.chunking.impl.image;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.common.domain.chunk.Chunk;
import com.knowledge.common.enums.chunk.ChunkContentType;
import com.knowledge.common.domain.preprocess.ViewElement;
import com.knowledge.common.domain.structure.UnifiedElement;
import com.knowledge.worker.chunking.SliceContext;
import com.knowledge.worker.chunking.slice.SliceStrategy;
import com.knowledge.common.enums.chunk.ChunkAlgorithm;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 图片切片器：一期 content = 图注占位说明（图片内文字不识别，OCR 预留），
 * 图片与图注保持关联（sourceElementIds 含图片元素，图注文本随片），不建多模态向量。
 *
 * @author cxxl
 */
@Component
public class ImageCaptionSliceStrategy implements SliceStrategy {

    @Override
    public ChunkAlgorithm algorithm() {
        return ChunkAlgorithm.IMAGE_CAPTION_PLACEHOLDER;
    }

    @Override
    public List<Chunk> slice(ViewElement element, SliceContext context) {
        UnifiedElement unified = context.getById().get(element.getElementId());
        String caption = ObjectUtil.isNull(unified) ? null : unified.getCaption();

        String content = "图片说明：[图片内文字未识别（OCR 预留，一期仅保留引用）]";
        if (StrUtil.isNotBlank(caption)) {
            content += "｜图注：" + caption;
        }

        Chunk chunk = new Chunk();
        chunk.setContent(content);
        chunk.setContentType(ChunkContentType.IMAGE.name());
        chunk.setTitlePath(String.join(" > ", context.getTitlePath()));
        chunk.setSourceElementIds(new ArrayList<>(List.of(element.getElementId())));
        if (ObjectUtil.isNotNull(element.getPage())) {
            chunk.setPageRange(List.of(element.getPage()));
        } else if (ObjectUtil.isNotNull(unified) && ObjectUtil.isNotNull(unified.getPageRange())
                && !unified.getPageRange().isEmpty()) {
            chunk.setPageRange(new ArrayList<>(unified.getPageRange()));
        }
        chunk.setCharCount(content.length());
        return List.of(chunk);
    }
}
