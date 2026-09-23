package com.knowledge.common.dto.response.stagecontent;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.util.List;

/**
 * 产物内容响应（step-12 B2，恢复 T3）：对比视图内容层数据源。
 * latest = 该次运行产物是否可用（按 task.productId 精确取产物；历史任务同样可展示）。
 *
 * @author cxxl
 */
@Data
public class StageContentVO {

    /** 文件结果 ID */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileResultId;

    /** 环节（白名单 PARSE/STRUCTURE/PREPROCESS/CHUNK/EMBED） */
    private String stage;

    /** 任务 ID（无任务时为空） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long taskId;

    /** 该次运行产物是否可用（false = 无任务/无产物/无 artifactId，items 为空） */
    private Boolean latest;

    /** 内容项列表（按集合内顺序） */
    private List<StageContentItemVO> items;
}
