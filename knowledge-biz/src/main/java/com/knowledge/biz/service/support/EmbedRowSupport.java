package com.knowledge.biz.service.support;

import com.knowledge.common.domain.embed.EmbeddingRecord;
import com.knowledge.common.domain.embed.EmbeddingSet;
import com.knowledge.common.domain.entity.KbEmbeddingRecord;
import com.knowledge.common.domain.entity.KbEmbeddingSet;
import com.knowledge.common.enums.task.RowStatus;

/**
 * 向量化落库行装配（种子注入与向量化任务共用）：集合行与记录行的字段口径只有一处。
 *
 * <p>按场景不同的项（主键、缓存命中、创建时间）不在这里，由调用侧补：
 * 种子注入自己生成主键与时间戳、缓存命中固定 false；任务执行按向量化结果原样带入。
 *
 * @author cxxl
 */
public final class EmbedRowSupport {

    private EmbedRowSupport() {
    }

    /**
     * 集合账本行（文件 + 集合 + 产物引用，状态 ACTIVE）。
     *
     * @param fileResultId    文件结果 ID
     * @param set             向量化集合（环节产物）
     * @param strategyVersion 生效策略版本（种子注入用内置版本口径）
     * @param artifactId      产物对象 ID
     */
    public static KbEmbeddingSet setRow(Long fileResultId, EmbeddingSet set, String strategyVersion,
                                        String artifactId) {
        KbEmbeddingSet row = new KbEmbeddingSet();
        row.setFileResultId(fileResultId);
        row.setChunkSetRef(set.getChunkSetRef());
        row.setEmbeddingSetId(set.getEmbeddingSetId());
        row.setStrategyVersion(strategyVersion);
        row.setModel(set.getModel());
        row.setDimension(set.getDimension());
        row.setMetric(set.getMetric());
        row.setNormalized(set.isNormalized());
        row.setRecordCount(set.getRecordCount());
        row.setCachedCount(set.getCachedCount());
        row.setStatus(RowStatus.ACTIVE.name());
        row.setArtifactId(artifactId);
        return row;
    }

    /**
     * 记录行（片定位 + 输入文本口径 + 请求号，状态原样带入）。
     *
     * @param embeddingSetId 所属集合行 ID
     * @param record         向量化记录（环节产物元素）
     */
    public static KbEmbeddingRecord recordRow(Long embeddingSetId, EmbeddingRecord record) {
        KbEmbeddingRecord row = new KbEmbeddingRecord();
        row.setEmbeddingSetId(embeddingSetId);
        row.setEmbeddingId(record.getEmbeddingId());
        row.setChunkId(record.getChunkId());
        row.setContentType(record.getContentType());
        row.setParentChunkId(record.getParentChunkId());
        row.setInputText(record.getInputText());
        row.setInputTextHash(record.getInputTextHash());
        row.setTokenCount(record.getTokenCount());
        row.setRequestId(record.getRequestId());
        row.setStatus(record.getStatus());
        return row;
    }
}
