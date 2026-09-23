package com.knowledge.biz.service.db;

import com.baomidou.mybatisplus.extension.service.IService;
import com.knowledge.common.domain.entity.KbIndexSet;

/**
 * 索引集合数据访问服务（step-13 B08）：一知识库一行，承载二级发布指针。
 *
 * @author cxxl
 */
public interface KbIndexSetDbService extends IService<KbIndexSet> {

    /**
     * 按知识库取集合行（无 → null）。
     */
    KbIndexSet getByKb(Long knowledgeBaseId);

    /**
     * 按知识库取集合行，不存在则创建（uk_kb 兜底并发：冲突后重查返回既有行）。
     */
    KbIndexSet getOrCreateByKb(Long knowledgeBaseId);

    /**
     * 更新二级发布指针（发布/回退共用）。
     */
    void updatePublishedVersion(Long setId, Long versionId);
}
