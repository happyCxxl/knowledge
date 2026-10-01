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
 * <p>**知识库与文档两类计数按可见范围过滤**（普通用户只数自己创建的库及其下文档，
 * 管理员数全部），与知识库列表页同一口径；策略版本数是平台级资产，不按归属过滤。
 *
 * @author cxxl
 */
@Data
public class HomeSummaryVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 可见范围内知识库总数（含已停用、含默认库） */
    private Long knowledgeBaseCount;

    /** 其中启用中的知识库数 */
    private Long enabledKnowledgeBaseCount;

    /** 可用策略版本数（四类合计，**只含启用中的**，与界面「可用策略」文案一致） */
    private Long strategyVersionCount;

    /** 预处理策略版本数 */
    private Long preprocessVersionCount;

    /** 切片策略版本数 */
    private Long chunkVersionCount;

    /** 向量化策略版本数 */
    private Long embedVersionCount;

    /** 检索策略版本数 */
    private Long retrievalVersionCount;

    /** 文档数：可见库下的 kb_file_result 行数（已建档文档，**不含文件校验失败**） */
    private Long documentCount;
}
