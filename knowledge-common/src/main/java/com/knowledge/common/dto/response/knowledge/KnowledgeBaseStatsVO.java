package com.knowledge.common.dto.response.knowledge;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 知识库统计视图对象（仅统计未删除数据）。
 *
 * @author cxxl
 */
@Data
public class KnowledgeBaseStatsVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 知识库总数（含已停用、含默认库） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long knowledgeBaseCount;

    /** 启用中的知识库数（status=1） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long enabledCount;

    /** 文档总数：kb_file_result 记录数（一次提交 = 一个任务 = 一行） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long documentCount;
}
