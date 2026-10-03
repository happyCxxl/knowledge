package com.knowledge.common.dto.request.stage;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 环节详情查询条件（解析 / 组装 / 预处理 / 切片 / 向量化 / 产物内容详情共用）。
 *
 * <p>taskId 可选：不传取该环节最新任务，传了则查该次运行。
 * page/limit 只在产物内容接口上生效，用于逐页取内容项（大文档不一次取回）；
 * docPage 同样只在产物内容接口上生效，用于只取某个文档页的元素。
 *
 * @author cxxl
 */
@Data
public class StageDetailQueryDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务 ID（可选；不传取最新任务，传了则查该次运行详情） */
    private Long taskId;

    /** 文档页码（可选；传了只回该页元素，从 1 起） */
    private Integer docPage;

    /** 页码（从 1 起；不传按 1） */
    private Integer page;

    /** 每页条数（不传按默认上限，超过上限按上限截断） */
    private Integer limit;
}
