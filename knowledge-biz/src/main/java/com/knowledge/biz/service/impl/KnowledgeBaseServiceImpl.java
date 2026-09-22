package com.knowledge.biz.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledge.biz.service.KnowledgeBaseService;
import com.knowledge.biz.service.db.KbAuditLogDbService;
import com.knowledge.biz.service.db.KnowledgeBaseDbService;
import com.knowledge.common.domain.entity.KnowledgeBase;
import com.knowledge.common.domain.rules.KnowledgeBaseRules;
import com.knowledge.common.dto.request.knowledge.KnowledgeBaseCreateDto;
import com.knowledge.common.dto.request.knowledge.KnowledgeBaseUpdateDto;
import com.knowledge.common.dto.response.knowledge.KnowledgeBaseVO;
import com.knowledge.common.enums.knowledge.AuditActionType;
import com.knowledge.common.enums.knowledge.KnowledgeBaseStatus;
import com.knowledge.common.security.KnowledgeUser;
import com.knowledge.common.security.SecurityUtils;
import com.knowledge.common.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库管理应用服务实现：管理闭环 + 同事务审计。
 *
 * @author cxxl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    /** 审计对象类型 */
    private static final String AUDIT_OBJECT_TYPE = "KNOWLEDGE_BASE";

    private final KnowledgeBaseDbService knowledgeBaseDbService;

    private final KbAuditLogDbService kbAuditLogDbService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(KnowledgeBaseCreateDto dto) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setName(dto.getName());
        kb.setDescription(dto.getDescription());
        kb.setStatus(KnowledgeBaseStatus.ACTIVE.getCode());
        kb.setStrategyBindingEnabled(resolveBindingEnabled(dto.getStrategyBindingEnabled()));
        // 默认库唯一产生途径是 seed；创建接口一律落普通库，default_flag 不接受入参
        kb.setDefaultFlag(0);
        kb.setUserId(currentUserId());
        knowledgeBaseDbService.save(kb);
        kbAuditLogDbService.saveAudit(AuditActionType.CREATE, AUDIT_OBJECT_TYPE, kb.getId(), null, JsonUtil.toJsonStr(kb));
        log.info("===> KnowledgeBaseServiceImpl create 创建知识库, id={}, name={}", kb.getId(), kb.getName());
        return kb.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(KnowledgeBaseUpdateDto dto) {
        KnowledgeBase kb = knowledgeBaseDbService.getActiveById(dto.getId());
        String beforeJson = JsonUtil.toJsonStr(kb);
        kb.setName(dto.getName());
        kb.setDescription(dto.getDescription());
        kb.setStrategyBindingEnabled(resolveBindingEnabled(dto.getStrategyBindingEnabled()));
        knowledgeBaseDbService.updateById(kb);
        kbAuditLogDbService.saveAudit(AuditActionType.UPDATE, AUDIT_OBJECT_TYPE, kb.getId(), beforeJson, JsonUtil.toJsonStr(kb));
        log.info("===> KnowledgeBaseServiceImpl update 更新知识库, id={}, name={}", kb.getId(), kb.getName());
        return true;
    }

    @Override
    public KnowledgeBaseVO detail(Long id) {
        return toVO(knowledgeBaseDbService.getActiveById(id));
    }

    @Override
    public IPage<KnowledgeBaseVO> page(long current, long size, String name) {
        IPage<KnowledgeBase> page = knowledgeBaseDbService.pageByName(current, size, name);
        Page<KnowledgeBaseVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<KnowledgeBaseVO> records = page.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        voPage.setRecords(records);
        return voPage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean disable(Long id) {
        KnowledgeBase kb = knowledgeBaseDbService.getActiveById(id);
        KnowledgeBaseRules.checkCanDisable(kb);
        KnowledgeBaseRules.checkNotDefault(kb);
        String beforeJson = JsonUtil.toJsonStr(kb);
        kb.setStatus(KnowledgeBaseStatus.DISABLED.getCode());
        knowledgeBaseDbService.updateById(kb);
        kbAuditLogDbService.saveAudit(AuditActionType.DISABLE, AUDIT_OBJECT_TYPE, id, beforeJson, JsonUtil.toJsonStr(kb));
        log.info("===> KnowledgeBaseServiceImpl disable 停用知识库, id={}", id);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean enable(Long id) {
        KnowledgeBase kb = knowledgeBaseDbService.getActiveById(id);
        KnowledgeBaseRules.checkCanEnable(kb);
        String beforeJson = JsonUtil.toJsonStr(kb);
        kb.setStatus(KnowledgeBaseStatus.ACTIVE.getCode());
        knowledgeBaseDbService.updateById(kb);
        kbAuditLogDbService.saveAudit(AuditActionType.ENABLE, AUDIT_OBJECT_TYPE, id, beforeJson, JsonUtil.toJsonStr(kb));
        log.info("===> KnowledgeBaseServiceImpl enable 启用知识库, id={}", id);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        KnowledgeBase kb = knowledgeBaseDbService.getActiveById(id);
        KnowledgeBaseRules.checkNotDefault(kb);
        knowledgeBaseDbService.removeById(id);
        kbAuditLogDbService.saveAudit(AuditActionType.DELETE, AUDIT_OBJECT_TYPE, id, JsonUtil.toJsonStr(kb), null);
        log.info("===> KnowledgeBaseServiceImpl delete 逻辑删除知识库, id={}", id);
        return true;
    }

    private KnowledgeBaseVO toVO(KnowledgeBase kb) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        vo.setId(kb.getId());
        vo.setName(kb.getName());
        vo.setDescription(kb.getDescription());
        vo.setStatus(kb.getStatus());
        vo.setStrategyBindingEnabled(resolveBindingEnabled(kb.getStrategyBindingEnabled()));
        vo.setDefaultFlag(kb.getDefaultFlag());
        vo.setUserId(kb.getUserId());
        vo.setCreateBy(kb.getCreateBy());
        vo.setCreateTime(kb.getCreateTime());
        vo.setUpdateBy(kb.getUpdateBy());
        vo.setUpdateTime(kb.getUpdateTime());
        return vo;
    }

    /** 开关归一化：null/非 0 一律视为开启（1） */
    private Integer resolveBindingEnabled(Integer value) {
        return Integer.valueOf(0).equals(value) ? 0 : 1;
    }

    /** 当前登录用户 ID（无认证上下文为 null） */
    private Long currentUserId() {
        KnowledgeUser user = SecurityUtils.getUser();
        return user == null ? null : user.getId();
    }
}
