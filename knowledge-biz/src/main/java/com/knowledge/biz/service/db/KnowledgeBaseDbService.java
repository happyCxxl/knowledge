package com.knowledge.biz.service.db;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.common.domain.entity.KnowledgeBase;
import com.knowledge.common.enums.knowledge.KnowledgeBaseSort;
import com.knowledge.infra.persistence.InfraDbService;

/**
 * 知识库数据访问服务（表：kb_knowledge_base）。
 *
 * @author cxxl
 */
public interface KnowledgeBaseDbService extends InfraDbService<KnowledgeBase> {

    /**
     * 分页查询（名称模糊 + 状态精确过滤；@TableLogic 自动过滤已逻辑删除）。
     *
     * @param current 当前页，从 1 开始
     * @param size    每页条数
     * @param name    名称关键字（模糊匹配；为空时不过滤）
     * @param status  状态：1 启用 / 0 停用；null 不过滤
     * @param sort    排序口径；null 按默认口径
     * @return 知识库分页
     */
    IPage<KnowledgeBase> pageByCondition(long current, long size, String name, Integer status,
                                        KnowledgeBaseSort sort);

    /**
     * 按状态统计知识库数（@TableLogic 自动过滤已逻辑删除）。
     *
     * @param status 状态：1 启用 / 0 停用；null 统计全部
     * @return 知识库数
     */
    long countByStatus(Integer status);

    /**
     * 获取存在且未删除的知识库（对象级获取语义，调用方免判空）。
     *
     * @param id 知识库 ID
     * @return 知识库实体
     * @apiNote 不存在或已删除抛 KnowledgeException（KB_NOT_FOUND 40401）
     */
    KnowledgeBase getActiveById(Long id);
}
