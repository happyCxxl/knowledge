package com.knowledge.common.dto.request.stage;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 环节详情查询条件（解析 / 组装 / 预处理 / 切片 / 向量化 / 产物内容详情共用）。
 *
 * <p>taskId 可选：不传取该环节最新任务，传了则查该次运行。
 *
 * @author cxxl
 */
@Data
public class StageDetailQueryDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务 ID（可选；不传取最新任务，传了则查该次运行详情） */
    private Long taskId;
}
