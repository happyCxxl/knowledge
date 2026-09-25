package com.knowledge.biz.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.common.dto.request.knowledge.KnowledgeBaseCreateDto;
import com.knowledge.common.dto.request.knowledge.KnowledgeBaseUpdateDto;
import com.knowledge.common.dto.request.knowledge.StrategyBindingUpdateDto;
import com.knowledge.common.dto.request.knowledge.StrategyBindingsUpdateRequest;
import com.knowledge.common.dto.response.knowledge.KnowledgeBaseStatsVO;
import com.knowledge.common.dto.response.knowledge.KnowledgeBaseVO;
import com.knowledge.common.dto.response.knowledge.StrategyBindingVO;
import com.knowledge.common.enums.knowledge.KnowledgeBaseSort;

/**
 * 知识库管理应用服务（创建/更新/详情/分页/启停/逻辑删除 + 同事务审计）。
 *
 * @author cxxl
 */
public interface KnowledgeBaseService {

    /**
     * 创建知识库（默认启用状态），返回知识库 ID。
     */
    Long create(KnowledgeBaseCreateDto dto);

    /**
     * 更新名称/描述/策略绑定开关。
     */
    @SuppressWarnings("SameReturnValue")
    boolean update(KnowledgeBaseUpdateDto dto);

    /**
     * 详情（已逻辑删除不可见）。
     */
    KnowledgeBaseVO detail(Long id);

    /**
     * 分页列表（名称模糊，不含已删），并回填每库文档数与已发布索引版本。
     *
     * @param current 当前页，从 1 开始
     * @param size    每页条数
     * @param name    名称模糊关键字（可空）
     * @param status  状态过滤：1 启用 / 0 停用；null 不过滤
     * @param sort    排序口径：null 或未知值按默认口径（默认库恒最前）
     * @return 知识库分页
     */
    IPage<KnowledgeBaseVO> page(long current, long size, String name, Integer status,
                                KnowledgeBaseSort sort);

    /**
     * 统计概览（仅未删除数据）：知识库总数、启用数、文档总数。
     */
    KnowledgeBaseStatsVO stats();

    /**
     * 停用（仅启用状态可停用）。
     */
    @SuppressWarnings("SameReturnValue")
    boolean disable(Long id);

    /**
     * 启用（仅停用状态可启用）。
     */
    @SuppressWarnings("SameReturnValue")
    boolean enable(Long id);

    /**
     * 逻辑删除（del_flag='1'，无恢复接口）。
     */
    @SuppressWarnings("SameReturnValue")
    boolean delete(Long id);

    /**
     * 查知识库某类型的策略绑定（未绑定返回仅含 strategyType 的空 VO）。
     */
    StrategyBindingVO strategyBinding(Long id, String strategyType);

    /**
     * 绑定/解绑知识库某类型策略：strategyVersionId 为 null 解绑（逻辑删除，重绑复用原行）；
     * 非法（类型白名单/版本不存在/类型不匹配/停用/绑定开关关闭）→ 40001。
     */
    @SuppressWarnings("SameReturnValue")
    boolean bindStrategy(Long id, StrategyBindingUpdateDto dto);

    /**
     * 批量设置知识库策略集合（发布=知识库策略集合）：仅处理请求中出现的类型（幂等局部更新），
     * 未提及类型不动；strategyVersionId 为 null = 解绑；逐类型审计 BIND。
     */
    @SuppressWarnings("SameReturnValue")
    boolean bindStrategies(Long id, StrategyBindingsUpdateRequest request);
}
