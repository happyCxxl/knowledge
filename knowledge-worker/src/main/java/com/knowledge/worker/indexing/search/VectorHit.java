package com.knowledge.worker.indexing.search;

import lombok.Builder;
import lombok.Data;

/**
 * 向量检索命中（step-13 B3）：返回字段原样入索引，命中直取不回查库。
 *
 * @author cxxl
 */
@Data
@Builder
public class VectorHit {

    /** 片 ID */
    private String chunkId;

    /** 相似度分数 */
    private Float score;

    /** 文件结果 ID */
    private Long documentId;

    /** 片类型 */
    private String contentType;

    /** 父片 ID */
    private String parentChunkId;

    /** 片文本 */
    private String content;

    /** 标题路径 */
    private String titlePath;

    /** 源元素 ID（JSON 串） */
    private String sourceElementIds;
}
