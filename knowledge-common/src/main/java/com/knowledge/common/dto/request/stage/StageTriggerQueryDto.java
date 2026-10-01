package com.knowledge.common.dto.request.stage;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 环节触发查询条件（切片 / 预处理 / 向量化三处触发入口共用）。
 *
 * <p>两项都是"可选覆盖"语义：不传时各环节按自己的策略解析档位与最新上游产物决定，
 * 摊在 Controller 签名上会与其它查询参数挤在一起，收成一个对象后签名只留路径参数。
 *
 * <p>{@code @ParameterObject} 让 Swagger 把字段展开成独立查询参数（与首页最近提交同一口径）。
 *
 * @author cxxl
 */
@Data
public class StageTriggerQueryDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 策略版本行 ID（可选；不传按该环节的策略解析档位：显式指定 > KB 绑定 > 启用中最新 > 内置默认） */
    private Long strategyVersionId;

    /** 上游产物 ID（可选；不传取该文件结果在当前环节上游环节的最新产物） */
    private Long upstreamProductId;
}
