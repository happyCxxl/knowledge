package com.knowledge.biz.service.db.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledge.biz.mapper.KbAuditLogMapper;
import com.knowledge.biz.service.db.KbAuditLogDbService;
import com.knowledge.common.domain.entity.KbAuditLog;
import com.knowledge.common.enums.knowledge.AuditActionType;
import com.knowledge.common.security.KnowledgeUser;
import com.knowledge.common.security.SecurityUtils;
import com.knowledge.infra.persistence.InfraDbServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 操作审计数据访问服务实现：审计行组装内聚在此，append-only。
 *
 * @author cxxl
 */
@Slf4j
@Service
public class KbAuditLogDbServiceImpl extends InfraDbServiceImpl<KbAuditLogMapper, KbAuditLog>
        implements KbAuditLogDbService {

    /** 无认证上下文（后台线程/测试）时的操作人兜底 */
    private static final String FALLBACK_OPERATOR = "system";

    @Override
    public void saveAudit(AuditActionType action, String objectType, Long objectId, String beforeJson, String afterJson) {
        KnowledgeUser user = currentUser();
        KbAuditLog audit = new KbAuditLog();
        audit.setActionType(action.name());
        audit.setObjectType(objectType);
        audit.setObjectId(String.valueOf(objectId));
        audit.setBeforeSummary(beforeJson);
        audit.setAfterSummary(afterJson);
        audit.setUserId(ObjectUtil.isNull(user) ? null : user.getId());
        audit.setCreateBy(operatorOf(user));
        save(audit);
    }

    @Override
    public IPage<KbAuditLog> pageActivities(long current, long size, String actionType, String operator,
                                            LocalDateTime beginTime, LocalDateTime endTime) {
        LambdaQueryWrapper<KbAuditLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StrUtil.isNotBlank(actionType), KbAuditLog::getActionType, actionType)
                .eq(StrUtil.isNotBlank(operator), KbAuditLog::getCreateBy, operator)
                .ge(ObjectUtil.isNotNull(beginTime), KbAuditLog::getCreateTime, beginTime)
                .le(ObjectUtil.isNotNull(endTime), KbAuditLog::getCreateTime, endTime)
                // 按 id 倒序而不是 create_time：同一秒内的多条（如发布批量触发）顺序才稳定
                .orderByDesc(KbAuditLog::getId);
        return page(new Page<>(current, size), queryWrapper);
    }

    /** 审计操作人：登录用户名优先；无认证上下文回退 system */
    private String operatorOf(KnowledgeUser user) {
        if (ObjectUtil.isNotNull(user) && StrUtil.isNotBlank(user.getUsername())) {
            return user.getUsername();
        }
        return FALLBACK_OPERATOR;
    }

    /** 取当前登录用户：无认证时可能为 null 或抛异常，统一防御 */
    private KnowledgeUser currentUser() {
        try {
            return SecurityUtils.getUser();
        } catch (Exception e) {
            log.warn("获取当前登录用户失败，审计操作人回退 system", e);
            return null;
        }
    }
}
