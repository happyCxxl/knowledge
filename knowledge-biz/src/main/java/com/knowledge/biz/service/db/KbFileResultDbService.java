package com.knowledge.biz.service.db;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.infra.persistence.InfraDbService;

import java.util.List;
import java.util.Map;

/**
 * 文件结果数据访问服务（kb_file_result）。
 *
 * @author cxxl
 */
public interface KbFileResultDbService extends InfraDbService<KbFileResult> {

    /** 取知识库下全部文件结果（id 升序；索引构建枚举数据源） */
    List<KbFileResult> listByKb(Long knowledgeBaseId);

    /**
     * 按知识库分页查文件结果（id 倒序，新→旧）。
     *
     * @param current        当前页，从 1 开始
     * @param size           每页条数
     * @param knowledgeBaseId 知识库 ID
     * @return 文件结果分页
     */
    IPage<KbFileResult> pageByKb(long current, long size, Long knowledgeBaseId);

    /**
     * 统计全库文档总数（一次提交 = 一个任务 = 一行；逻辑删除自动排除）。
     *
     * @return 文档总数
     */
    long countAll();

    /**
     * 统计单库文档数（一次提交 = 一个任务 = 一行；逻辑删除自动排除）。
     *
     * @param knowledgeBaseId 知识库 ID
     * @return 文档数
     */
    long countByKb(Long knowledgeBaseId);

    /**
     * 批量统计多个知识库的文档数，供列表回填（避免逐行查询）。
     *
     * @param knowledgeBaseIds 知识库 ID 列表
     * @return 知识库 ID → 文档数；无文档的知识库不出现在结果中
     */
    Map<Long, Long> countGroupByKb(List<Long> knowledgeBaseIds);
}
