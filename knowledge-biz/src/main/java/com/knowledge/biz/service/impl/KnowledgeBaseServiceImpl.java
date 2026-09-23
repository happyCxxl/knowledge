package com.knowledge.biz.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledge.biz.service.KnowledgeBaseService;
import com.knowledge.biz.service.StrategyVersionService;
import com.knowledge.biz.service.db.KbAuditLogDbService;
import com.knowledge.biz.service.db.KbPipelineStrategyVersionDbService;
import com.knowledge.biz.service.db.KbStrategyBindingDbService;
import com.knowledge.biz.service.db.KnowledgeBaseDbService;
import com.knowledge.common.domain.entity.KbPipelineStrategyVersion;
import com.knowledge.common.domain.entity.KbStrategyBinding;
import com.knowledge.common.domain.entity.KnowledgeBase;
import com.knowledge.common.domain.rules.KnowledgeBaseRules;
import com.knowledge.common.dto.request.knowledge.KnowledgeBaseCreateDto;
import com.knowledge.common.dto.request.knowledge.KnowledgeBaseUpdateDto;
import com.knowledge.common.dto.request.knowledge.StrategyBindingUpdateDto;
import com.knowledge.common.dto.response.knowledge.KnowledgeBaseVO;
import com.knowledge.common.dto.response.knowledge.StrategyBindingVO;
import com.knowledge.common.enums.knowledge.AuditActionType;
import com.knowledge.common.enums.knowledge.KnowledgeBaseStatus;
import com.knowledge.common.enums.task.RowStatus;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.ThrowUtil;
import com.knowledge.common.security.KnowledgeUser;
import com.knowledge.common.security.SecurityUtils;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.worker.chunking.strategy.ChunkStrategy;
import com.knowledge.worker.embedding.strategy.EmbedStrategy;
import com.knowledge.worker.preprocessing.strategy.PreprocessStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
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

    private final KbStrategyBindingDbService strategyBindingDbService;

    private final KbPipelineStrategyVersionDbService strategyVersionDbService;

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
        KnowledgeBaseVO vo = toVO(knowledgeBaseDbService.getActiveById(id));
        fillStrategyBindings(vo);
        return vo;
    }

    @Override
    public IPage<KnowledgeBaseVO> page(long current, long size, String name) {
        IPage<KnowledgeBase> page = knowledgeBaseDbService.pageByName(current, size, name);
        Page<KnowledgeBaseVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        List<KnowledgeBaseVO> records = page.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        fillStrategyBindings(records);
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

    @Override
    public StrategyBindingVO strategyBinding(Long id, String strategyType) {
        knowledgeBaseDbService.getActiveById(id);
        ThrowUtil.throwIf(StrUtil.isBlank(strategyType)
                        || !StrategyVersionService.BINDABLE_TYPES.contains(strategyType),
                ErrorCode.PARAM_INVALID, "未知策略类型: " + strategyType);
        StrategyBindingVO vo = new StrategyBindingVO();
        vo.setStrategyType(strategyType);
        KbStrategyBinding binding = strategyBindingDbService.getByKbAndType(id, strategyType);
        if (binding != null) {
            KbPipelineStrategyVersion version = strategyVersionDbService.getById(binding.getStrategyVersionId());
            if (version != null) {
                vo.setStrategyVersionId(version.getId());
                vo.setStrategyName(version.getName());
                vo.setStrategyVersion(version.getVersion());
            }
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean bindStrategy(Long id, StrategyBindingUpdateDto dto) {
        KnowledgeBase kb = knowledgeBaseDbService.getActiveById(id);
        ThrowUtil.throwIf(!isBindingEnabled(kb),
                ErrorCode.PARAM_INVALID, "该知识库已关闭策略绑定（评测模式），禁止绑定策略");
        String type = dto.getStrategyType();
        ThrowUtil.throwIf(!StrategyVersionService.BINDABLE_TYPES.contains(type),
                ErrorCode.PARAM_INVALID, "未知策略类型: " + type);

        // 解绑：绑定行逻辑删除（无绑定行时幂等成功）
        if (dto.getStrategyVersionId() == null) {
            KbStrategyBinding existing = strategyBindingDbService.getByKbAndType(id, type);
            String before = bindingSummary(existing);
            if (existing != null) {
                existing.setDelFlag("1");
                strategyBindingDbService.updateById(existing);
            }
            kbAuditLogDbService.saveAudit(AuditActionType.BIND, AUDIT_OBJECT_TYPE, id, type + "=" + before, null);
            log.info("===> KnowledgeBaseServiceImpl bindStrategy 解绑知识库策略, id={}, type={}", id, type);
            return true;
        }

        KbPipelineStrategyVersion version = strategyVersionDbService.getById(dto.getStrategyVersionId());
        ThrowUtil.throwIf(version == null, ErrorCode.PARAM_INVALID, "策略版本不存在");
        ThrowUtil.throwIf(!type.equals(version.getType()), ErrorCode.PARAM_INVALID, "策略类型与所选版本不匹配");
        ThrowUtil.throwIf(!RowStatus.ACTIVE.name().equals(version.getStatus()),
                ErrorCode.PARAM_INVALID, "策略已停用，请先启用后再绑定");

        // 绑定/重绑：复用原行（含已逻辑删除行），uk 不被占用
        KbStrategyBinding existing = strategyBindingDbService.getAnyByKbAndType(id, type);
        String before = bindingSummary(existing);
        if (existing == null) {
            KbStrategyBinding binding = new KbStrategyBinding();
            binding.setKnowledgeBaseId(id);
            binding.setStrategyType(type);
            binding.setStrategyVersionId(dto.getStrategyVersionId());
            strategyBindingDbService.save(binding);
        } else {
            existing.setDelFlag("0");
            existing.setStrategyVersionId(dto.getStrategyVersionId());
            strategyBindingDbService.updateById(existing);
        }
        String after = version.getName() + "-" + version.getVersion();
        kbAuditLogDbService.saveAudit(AuditActionType.BIND, AUDIT_OBJECT_TYPE, id, type + "=" + before, type + "=" + after);
        log.info("===> KnowledgeBaseServiceImpl bindStrategy 绑定知识库策略, id={}, type={}, versionId={}",
                id, type, dto.getStrategyVersionId());
        return true;
    }

    /** 绑定摘要（审计用；null/已删 → 无） */
    private String bindingSummary(KbStrategyBinding binding) {
        if (binding == null || "1".equals(binding.getDelFlag())) {
            return "无";
        }
        KbPipelineStrategyVersion version = strategyVersionDbService.getById(binding.getStrategyVersionId());
        return version == null ? "失效" : version.getName() + "-" + version.getVersion();
    }

    /** 详情：填充预处理/切片/向量化策略绑定摘要 */
    private void fillStrategyBindings(KnowledgeBaseVO vo) {
        fillBinding(vo, PreprocessStrategy.TYPE);
        fillBinding(vo, ChunkStrategy.TYPE);
        fillBinding(vo, EmbedStrategy.TYPE);
    }

    /** 分页：批量填充预处理/切片/向量化策略绑定摘要（避免逐行查询） */
    private void fillStrategyBindings(List<KnowledgeBaseVO> records) {
        if (records.isEmpty()) {
            return;
        }
        List<Long> kbIds = records.stream().map(KnowledgeBaseVO::getId).toList();
        fillBindingForType(records, kbIds, PreprocessStrategy.TYPE);
        fillBindingForType(records, kbIds, ChunkStrategy.TYPE);
        fillBindingForType(records, kbIds, EmbedStrategy.TYPE);
    }

    private void fillBindingForType(List<KnowledgeBaseVO> records, List<Long> kbIds, String type) {
        Map<Long, KbStrategyBinding> bindingByKb = strategyBindingDbService
                .listActiveByTypeAndKbIds(type, kbIds).stream()
                .collect(Collectors.toMap(KbStrategyBinding::getKnowledgeBaseId, Function.identity(), (a, b) -> a));
        if (bindingByKb.isEmpty()) {
            return;
        }
        List<Long> versionIds = bindingByKb.values().stream()
                .map(KbStrategyBinding::getStrategyVersionId)
                .distinct()
                .toList();
        Map<Long, KbPipelineStrategyVersion> versionById = strategyVersionDbService.listByIds(versionIds).stream()
                .collect(Collectors.toMap(KbPipelineStrategyVersion::getId, Function.identity(), (a, b) -> a));
        for (KnowledgeBaseVO vo : records) {
            KbStrategyBinding binding = bindingByKb.get(vo.getId());
            if (binding == null) {
                continue;
            }
            KbPipelineStrategyVersion version = versionById.get(binding.getStrategyVersionId());
            if (version == null) {
                continue;
            }
            applyBinding(vo, version);
        }
    }

    private void fillBinding(KnowledgeBaseVO vo, String type) {
        KbStrategyBinding binding = strategyBindingDbService.getByKbAndType(vo.getId(), type);
        KbPipelineStrategyVersion version = binding == null
                ? null : strategyVersionDbService.getById(binding.getStrategyVersionId());
        if (version == null) {
            return;
        }
        applyBinding(vo, version);
    }

    /** 绑定摘要字段分发：按策略版本行类型落 VO 字段 */
    private void applyBinding(KnowledgeBaseVO vo, KbPipelineStrategyVersion version) {
        String fullVersion = version.getName() + "-" + version.getVersion();
        if (ChunkStrategy.TYPE.equals(version.getType())) {
            vo.setChunkStrategyVersionId(version.getId());
            vo.setChunkStrategyVersion(fullVersion);
        } else if (EmbedStrategy.TYPE.equals(version.getType())) {
            vo.setEmbedStrategyVersionId(version.getId());
            vo.setEmbedStrategyVersion(fullVersion);
        } else {
            vo.setPreprocessStrategyVersionId(version.getId());
            vo.setPreprocessStrategyVersion(fullVersion);
        }
    }

    /** 绑定开关是否开启（null 视为开启，兼容存量数据） */
    private boolean isBindingEnabled(KnowledgeBase kb) {
        return kb == null || !Integer.valueOf(0).equals(kb.getStrategyBindingEnabled());
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
