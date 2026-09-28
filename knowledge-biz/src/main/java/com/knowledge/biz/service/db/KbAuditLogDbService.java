package com.knowledge.biz.service.db;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.common.domain.entity.KbAuditLog;
import com.knowledge.common.enums.knowledge.AuditActionType;
import com.knowledge.infra.persistence.InfraDbService;

import java.time.LocalDateTime;

/**
 * 操作审计数据访问服务（表：kb_audit_log）。
 *
 * @author cxxl
 */
public interface KbAuditLogDbService extends InfraDbService<KbAuditLog> {

    /**
     * 组装并落一条审计记录（与业务写入同事务，事务边界在业务 service）。
     *
     * @param action     审计动作
     * @param objectType 对象类型（如 KNOWLEDGE_BASE）
     * @param objectId   对象 ID
     * @param beforeJson 变更前摘要（JSON 文本，可空）
     * @param afterJson  变更后摘要（JSON 文本，可空）
     */
    void saveAudit(AuditActionType action, String objectType, Long objectId, String beforeJson, String afterJson);

    /**
     * 分页查询审计记录（首页行为记录数据源，新→旧）。
     *
     * <p>查询条件都可空，空即不过滤。
     *
     * @param current    当前页
     * @param size       每页条数
     * @param actionType 动作类型（AuditActionType 枚举名）
     * @param operator   操作人用户名
     * @param beginTime  起始时间（含）
     * @param endTime    结束时间（含）
     */
    IPage<KbAuditLog> pageActivities(long current, long size, String actionType, String operator,
                                     LocalDateTime beginTime, LocalDateTime endTime);
}
