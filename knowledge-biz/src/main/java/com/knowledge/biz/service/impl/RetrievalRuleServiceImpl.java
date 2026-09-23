package com.knowledge.biz.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.knowledge.biz.service.RetrievalRuleService;
import com.knowledge.biz.service.StrategyVersionService;
import com.knowledge.biz.service.db.KbAuditLogDbService;
import com.knowledge.biz.service.db.KbIndexSetDbService;
import com.knowledge.biz.service.db.KbIndexVersionDbService;
import com.knowledge.biz.service.db.KbPipelineStrategyVersionDbService;
import com.knowledge.common.domain.entity.KbIndexSet;
import com.knowledge.common.domain.entity.KbIndexVersion;
import com.knowledge.common.domain.entity.KbPipelineStrategyVersion;
import com.knowledge.common.dto.request.retrieval.RetrievalRulePublishDto;
import com.knowledge.common.dto.response.retrieval.RetrievalRulePublishVO;
import com.knowledge.common.dto.response.strategy.StrategyVersionVO;
import com.knowledge.common.enums.knowledge.AuditActionType;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.ThrowUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 检索规则服务实现（step-14 B2）：规则列表复用策略版本机制（RETRIEVAL 白名单）；
 * 选优发布 = 版本行 default_rule_id 切换 + 审计（规则行不可变，发布即切指针，评测可复现）。
 *
 * @author cxxl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalRuleServiceImpl implements RetrievalRuleService {

    private static final String AUDIT_OBJECT_TYPE = "INDEX_VERSION";

    private final StrategyVersionService strategyVersionService;

    private final KbPipelineStrategyVersionDbService strategyVersionDbService;

    private final KbIndexVersionDbService indexVersionDbService;

    private final KbIndexSetDbService indexSetDbService;

    private final KbAuditLogDbService kbAuditLogDbService;

    /**
     * 规则列表（type=RETRIEVAL；includeInactive=false 只启用中——测试台选择器用，true 全部——管理页用）。
     */
    public List<StrategyVersionVO> listRules(boolean includeInactive) {
        return strategyVersionService.list("RETRIEVAL", includeInactive);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RetrievalRulePublishVO publishDefaultRule(Long knowledgeBaseId, RetrievalRulePublishDto dto) {
        KbIndexVersion version = indexVersionDbService.getById(dto.getVersionId());
        ThrowUtil.throwIf(ObjectUtil.isNull(version), ErrorCode.INDEX_VERSION_NOT_FOUND);
        KbIndexSet set = indexSetDbService.getById(version.getIndexSetId());
        ThrowUtil.throwIf(ObjectUtil.isNull(set)
                        || !Objects.equals(set.getKnowledgeBaseId(), knowledgeBaseId),
                ErrorCode.INDEX_VERSION_NOT_FOUND, "版本行不属于该知识库");

        KbPipelineStrategyVersion rule = strategyVersionDbService.getById(dto.getRuleId());
        ThrowUtil.throwIf(ObjectUtil.isNull(rule) || !"RETRIEVAL".equals(rule.getType()),
                ErrorCode.RETRIEVAL_RULE_NOT_FOUND);

        Long oldRuleId = version.getDefaultRuleId();
        version.setDefaultRuleId(rule.getId());
        indexVersionDbService.updateById(version);
        kbAuditLogDbService.saveAudit(AuditActionType.RETRIEVAL_RULE_PUBLISH, AUDIT_OBJECT_TYPE,
                version.getId(), oldRuleId == null ? null : String.valueOf(oldRuleId),
                String.valueOf(rule.getId()));
        log.info("===> RetrievalRuleServiceImpl 选优发布完成, kbId={}, versionId={}, versionNo={}, rule={}-{}, oldRuleId={}",
                knowledgeBaseId, version.getId(), version.getVersionNo(), rule.getName(), rule.getVersion(), oldRuleId);

        RetrievalRulePublishVO vo = new RetrievalRulePublishVO();
        vo.setVersionId(version.getId());
        vo.setVersionNo(version.getVersionNo());
        vo.setRuleId(rule.getId());
        vo.setRuleNameVersion(rule.getName() + "-" + rule.getVersion());
        return vo;
    }
}
