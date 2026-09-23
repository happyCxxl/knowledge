package com.knowledge.common.dto.response.chunk;

import lombok.Data;

/**
 * 切片项 VO：kb_chunk 行的展示副本（检索命中最小单元）。
 *
 * @author cxxl
 */
@Data
public class ChunkItemVO {

    /** 集合内切片 ID（顺序号，集合内唯一） */
    private String chunkId;

    /** 父片 ID（父子层级；父片本身为空） */
    private String parentChunkId;

    /** 切片内容（normalizedText 口径） */
    private String content;

    /** 内容类型（ChunkContentType 枚举名） */
    private String contentType;

    /** 标题路径（最多 3 级） */
    private String titlePath;

    /** 来源元素 ID（JSON 数组字符串） */
    private String sourceElementIds;

    /** 页码范围（如 1-3） */
    private String pageRange;

    /** 表格引用（B03 表元素 ID） */
    private String tableRef;

    /** 集合内顺序 */
    private Integer orderNo;

    /** 字符数 */
    private Integer charCount;

    /** Token 估算（字符数÷1.5） */
    private Integer tokenCount;
}
