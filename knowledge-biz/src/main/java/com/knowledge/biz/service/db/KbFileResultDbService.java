package com.knowledge.biz.service.db;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.infra.persistence.InfraDbService;

/**
 * 文件结果数据访问服务（kb_file_result）。
 *
 * @author cxxl
 */
public interface KbFileResultDbService extends InfraDbService<KbFileResult> {

    /**
     * 按知识库分页查文件结果（id 倒序，新→旧）。
     *
     * @param current        当前页，从 1 开始
     * @param size           每页条数
     * @param knowledgeBaseId 知识库 ID
     * @return 文件结果分页
     */
    IPage<KbFileResult> pageByKb(long current, long size, Long knowledgeBaseId);
}
