package com.knowledge.common.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 阶段产物：本体在产物存储（sha256 寻址），本表只存引用与血缘。
 * 机器表：后台线程写入、append-only、不逻辑删——不挂平台五件套（仅 create_time）。
 *
 * @author cxxl
 */
@Data
@TableName("kb_pipeline_product")
public class KbPipelineProduct {

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 所属文件结果 */
    private Long fileResultId;

    /** 产出环节（PipelineStage 枚举名） */
    private String stage;

    /** 上游产物 ID（可空；PARSE 无上游） */
    private Long upstreamProductId;

    /** 能力/策略版本快照（JSON 文本） */
    private String capabilitySnapshot;

    /** 产物存储引用（sha256 寻址 key） */
    private String artifactId;

    /** 产物内容指纹（sha256） */
    private String contentHash;

    /** 状态（ACTIVE） */
    private String status;

    /** 创建时间（DB 默认填充） */
    private LocalDateTime createTime;
}
