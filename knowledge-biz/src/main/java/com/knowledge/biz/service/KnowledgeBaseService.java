package com.knowledge.biz.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.common.dto.request.knowledge.KnowledgeBaseCreateDto;
import com.knowledge.common.dto.request.knowledge.KnowledgeBaseUpdateDto;
import com.knowledge.common.dto.response.knowledge.KnowledgeBaseVO;

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
    boolean update(KnowledgeBaseUpdateDto dto);

    /**
     * 详情（已逻辑删除不可见）。
     */
    KnowledgeBaseVO detail(Long id);

    /**
     * 分页列表（名称模糊，不含已删）。
     */
    IPage<KnowledgeBaseVO> page(long current, long size, String name);

    /**
     * 停用（仅启用状态可停用）。
     */
    boolean disable(Long id);

    /**
     * 启用（仅停用状态可启用）。
     */
    boolean enable(Long id);

    /**
     * 逻辑删除（del_flag='1'，无恢复接口）。
     */
    boolean delete(Long id);
}
