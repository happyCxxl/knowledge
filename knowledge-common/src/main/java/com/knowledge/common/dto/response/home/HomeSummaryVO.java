package com.knowledge.common.dto.response.home;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 首页资产速览视图对象。
 *
 * <p>首页只展示"有多少资产"这一档，不做环节分布与待处理聚合（那些在各业务页各自呈现）。
 * 每一类都给出细分，避免用户为了看细分还要再点进对应页面。
 *
 * @author cxxl
 */
@Data
public class HomeSummaryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 知识库总数（含已停用、含默认库） */
    private Long knowledgeBaseCount;

    /** 启用中的知识库数 */
    private Long enabledKnowledgeBaseCount;

    /** 策略版本总数（四类合计，含已停用） */
    private Long strategyVersionCount;

    /** 预处理策略版本数 */
    private Long preprocessVersionCount;

    /** 切片策略版本数 */
    private Long chunkVersionCount;

    /** 向量化策略版本数 */
    private Long embedVersionCount;

    /** 检索策略版本数 */
    private Long retrievalVersionCount;

    /** 索引版本总数（含已退役） */
    private Long indexVersionCount;

    /** 在线索引版本数（按二级发布指针命中，正常为 0 或 1） */
    private Long onlineIndexVersionCount;

    /** 文档提交总数：kb_file_result 记录数（一次提交 = 一个任务 = 一行） */
    private Long documentCount;
}
