package com.knowledge.common.dto.response.knowledge;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 知识库-策略绑定视图：未绑定时仅返回 strategyType。
 *
 * <p>既能表达"**某个库**绑了什么"（单体查询），也能表达"**一批库**各绑了什么"
 * （批量查询，见 {@code GET /knowledge-base/strategy-bindings}）——
 * 后者靠 {@link #knowledgeBaseId} 区分每一行。批量接口一次返回该策略类型下所有
 * 已绑定的库，让前端不必按库逐个请求（原先切一次策略 tab 要发 N 次请求）。
 *
 * @author cxxl
 */
@Data
public class StrategyBindingVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 知识库 ID（雪花 ID）。
     *
     * <p>单体查询（按库查）时该字段为 null —— 库 ID 由请求路径给出，返回里无需重复；
     * 批量查询时**必有值**，是区分各行的唯一标识。
     */
    private Long knowledgeBaseId;

    /** 策略类型：PREPROCESS / CHUNK */
    private String strategyType;

    /** 绑定的策略版本行 ID（雪花 ID） */
    private Long strategyVersionId;

    /** 绑定策略名 */
    private String strategyName;

    /** 绑定策略版本号 */
    private String strategyVersion;
}
