package com.knowledge.common.dto.response.input;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.knowledge.common.dto.response.task.StageStatusVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件结果 VO：执行链工作台列表行（kb_file_result + kb_source_file 摘要 + 各环节最新任务状态）。
 * id 类字段经 ToStringSerializer 转字符串，防前端 JS 精度丢失。
 *
 * @author cxxl
 */
@Data
public class FileResultVO {

    /** 文件结果 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 所属知识库 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long knowledgeBaseId;

    /** 原始文件引用（kb_source_file.id） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceFileId;

    /** 文件 ID */
    private String fileId;

    /** 文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 各环节最新任务状态（无任务的环节不出现在列表中） */
    private List<StageStatusVO> stageStatuses;
}
