package com.knowledge.biz.service.db;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.common.domain.entity.KbSubmitLog;
import com.knowledge.infra.persistence.InfraDbService;

/**
 * 提交日志数据访问服务（kb_submit_log）：append-only，只新增与查询。
 *
 * @author cxxl
 */
public interface KbSubmitLogDbService extends InfraDbService<KbSubmitLog> {

    /**
     * 按幂等键查提交日志。
     *
     * @param requestId 幂等键（uk_request_id 唯一）
     * @return 提交日志实体；不存在返回 null
     */
    KbSubmitLog getByRequestId(String requestId);

    /**
     * 按知识库分页查提交记录（id 倒序，新→旧）。
     *
     * @param current        当前页，从 1 开始
     * @param size           每页条数
     * @param knowledgeBaseId 知识库 ID
     * @param status         提交结果筛选（PASS/FAIL；为空不过滤）
     * @param fileName       文件名模糊筛选（为空不过滤）
     * @return 提交日志分页
     */
    IPage<KbSubmitLog> pageByKb(long current, long size, Long knowledgeBaseId, String status, String fileName);
}
