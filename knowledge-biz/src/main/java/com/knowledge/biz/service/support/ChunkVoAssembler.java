package com.knowledge.biz.service.support;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.common.domain.entity.KbChunk;
import com.knowledge.common.domain.entity.KbChunkSet;
import com.knowledge.common.dto.response.chunk.ChunkItemVO;
import com.knowledge.common.dto.response.chunk.ChunkSummaryVO;
import com.knowledge.common.enums.chunk.ChunkContentType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 切片详情 VO 组装器（纯映射，不查库）：切片集合/切片行 → 统计/切片列表视图。
 * 集合与切片由 Service 查好传入；任务/子步骤字段组装不归本类。
 *
 * @author cxxl
 */
@Component
public class ChunkVoAssembler {

    /** 切片统计：总数/总字符/平均片长/父子数/类型分布（父子口径与 ChunkContentType 一致） */
    public ChunkSummaryVO toSummary(KbChunkSet chunkSet, List<KbChunk> chunks) {
        ChunkSummaryVO vo = new ChunkSummaryVO();
        vo.setChunkCount(chunkSet.getChunkCount());
        vo.setTotalChars(chunkSet.getTotalChars());
        // 父片 = SECTION；子片 = parentChunkId 非空；无章节归属的孤儿子片两者皆非（不计入父子数）
        int parentCount = 0;
        int childCount = 0;
        Map<String, Integer> typeCounts = new HashMap<>();
        for (KbChunk chunk : chunks) {
            if (ChunkContentType.SECTION.name().equals(chunk.getContentType())) {
                parentCount++;
            } else if (StrUtil.isNotBlank(chunk.getParentChunkId())) {
                childCount++;
            }
            typeCounts.merge(chunk.getContentType(), 1, Integer::sum);
        }
        vo.setParentChunkCount(parentCount);
        vo.setChildChunkCount(childCount);
        int chunkCount = ObjectUtil.defaultIfNull(chunkSet.getChunkCount(), 0);
        vo.setAvgChars(chunkCount > 0 && ObjectUtil.isNotNull(chunkSet.getTotalChars())
                ? (int) Math.round((double) chunkSet.getTotalChars() / chunkCount)
                : 0);
        vo.setTypeCounts(typeCounts);
        return vo;
    }

    /** 切片行 → 切片条目 VO 列表 */
    public List<ChunkItemVO> toChunkItemVOs(List<KbChunk> chunks) {
        return ObjectUtil.defaultIfNull(chunks, new ArrayList<KbChunk>()).stream()
                .map(this::toChunkItemVO)
                .toList();
    }

    private ChunkItemVO toChunkItemVO(KbChunk chunk) {
        ChunkItemVO vo = new ChunkItemVO();
        vo.setChunkId(chunk.getChunkId());
        vo.setParentChunkId(chunk.getParentChunkId());
        vo.setContent(chunk.getContent());
        vo.setContentType(chunk.getContentType());
        vo.setTitlePath(chunk.getTitlePath());
        vo.setSourceElementIds(chunk.getSourceElementIds());
        vo.setPageRange(chunk.getPageRange());
        vo.setTableRef(chunk.getTableRef());
        vo.setOrderNo(chunk.getOrderNo());
        vo.setCharCount(chunk.getCharCount());
        vo.setTokenCount(chunk.getTokenCount());
        return vo;
    }
}
