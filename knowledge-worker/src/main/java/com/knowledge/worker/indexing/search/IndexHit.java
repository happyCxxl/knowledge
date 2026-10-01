package com.knowledge.worker.indexing.search;

import com.knowledge.vector.CollectionRow;

/**
 * 检索命中公共字段契约：向量命中（{@link VectorHit}）与全文命中（{@link FullTextHit}）字段口径一致
 * （片 ID / 文档 / 类型 / 父片 / 内容 / 标题路径 / 来源元素），差别只在向量路多一个相似度分数。
 * 公共字段的"索引行 → 命中"映射集中在这里，读取方（如检索结果转 VO）也能统一按本契约处理。
 *
 * @author cxxl
 */
public interface IndexHit {

    String getChunkId();

    void setChunkId(String chunkId);

    Long getDocumentId();

    void setDocumentId(Long documentId);

    String getContentType();

    void setContentType(String contentType);

    String getParentChunkId();

    void setParentChunkId(String parentChunkId);

    String getContent();

    void setContent(String content);

    String getTitlePath();

    void setTitlePath(String titlePath);

    String getSourceElementIds();

    void setSourceElementIds(String sourceElementIds);

    /**
     * 索引行 → 命中公共字段（字段原样入索引，命中直取不回查库）。
     *
     * @param row 索引行（向量路为其子类 ScoredRow）
     */
    default void fillFrom(CollectionRow row) {
        setChunkId(row.getId());
        setDocumentId(row.getDocumentId());
        setContentType(row.getContentType());
        setParentChunkId(row.getParentChunkId());
        setContent(row.getContent());
        setTitlePath(row.getTitlePath());
        setSourceElementIds(row.getSourceElementIds());
    }
}
