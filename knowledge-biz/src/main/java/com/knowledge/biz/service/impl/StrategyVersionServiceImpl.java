package com.knowledge.biz.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.biz.service.StrategyVersionService;
import com.knowledge.biz.service.db.KbPipelineStrategyVersionDbService;
import com.knowledge.biz.service.db.KbStrategyBindingDbService;
import com.knowledge.common.domain.entity.KbPipelineStrategyVersion;
import com.knowledge.common.dto.request.strategy.StrategyVersionCreateDto;
import com.knowledge.common.dto.request.strategy.StrategyVersionUpdateDto;
import com.knowledge.common.dto.response.strategy.StrategyVersionVO;
import com.knowledge.common.enums.task.RowStatus;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import com.knowledge.common.exception.ThrowUtil;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.worker.chunking.strategy.ChunkAlgorithmSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 策略版本服务实现（管理 + 查询）。
 * 口径：策略版本行不可变——编辑 = 复制新行（校验新版本号不撞唯一键 → 插入新行，旧行原样保留）；
 * 停用代替删除（有绑定引用禁物理删除）；启停保留（运营开关，不改策略内容）。
 *
 * @author cxxl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyVersionServiceImpl implements StrategyVersionService {

    private final KbPipelineStrategyVersionDbService strategyVersionDbService;

    private final KbStrategyBindingDbService strategyBindingDbService;

    @Override
    public List<StrategyVersionVO> list(String type, boolean includeInactive) {
        ThrowUtil.throwIf(StrUtil.isBlank(type) || !SUPPORTED_TYPES.contains(type),
                ErrorCode.PARAM_INVALID, "未知策略类型: " + type);
        List<KbPipelineStrategyVersion> rows = includeInactive
                ? strategyVersionDbService.listByType(type)
                : strategyVersionDbService.listEnabledByType(type);
        return rows.stream().map(this::toVO).toList();
    }

    @Override
    public StrategyVersionVO create(StrategyVersionCreateDto dto) {
        ThrowUtil.throwIf(!SUPPORTED_TYPES.contains(dto.getType()),
                ErrorCode.PARAM_INVALID, "未知策略类型: " + dto.getType());
        String configSnapshot = resolveConfigSnapshot(dto.getType(), dto.getConfigSnapshot());
        KbPipelineStrategyVersion row = new KbPipelineStrategyVersion();
        row.setType(dto.getType());
        row.setName(dto.getName().trim());
        row.setVersion(dto.getVersion().trim());
        row.setConfigSnapshot(configSnapshot);
        row.setStatus(RowStatus.ACTIVE.name());
        try {
            strategyVersionDbService.save(row);
        } catch (DuplicateKeyException e) {
            throw new KnowledgeException(ErrorCode.PARAM_INVALID, "同环节同名版本已存在");
        }
        return toVO(row);
    }

    @Override
    public StrategyVersionVO update(Long id, StrategyVersionUpdateDto dto) {
        // 策略版本行不可变：编辑 = 复制新行（旧行原样保留；新版本号撞唯一键 → 40001）
        KbPipelineStrategyVersion old = requireById(id);
        String configSnapshot = resolveConfigSnapshot(old.getType(), dto.getConfigSnapshot());
        KbPipelineStrategyVersion row = new KbPipelineStrategyVersion();
        row.setType(old.getType());
        row.setName(dto.getName().trim());
        row.setVersion(dto.getVersion().trim());
        row.setConfigSnapshot(configSnapshot);
        row.setStatus(RowStatus.ACTIVE.name());
        try {
            strategyVersionDbService.save(row);
        } catch (DuplicateKeyException e) {
            throw new KnowledgeException(ErrorCode.PARAM_INVALID, "同环节同名版本已存在");
        }
        log.info("===> StrategyVersionServiceImpl 策略编辑=复制新版本, oldId={}, oldVersion={}, newName={}, newVersion={}",
                old.getId(), old.getVersion(), row.getName(), row.getVersion());
        return toVO(row);
    }

    @Override
    public StrategyVersionVO enable(Long id) {
        KbPipelineStrategyVersion row = requireById(id);
        row.setStatus(RowStatus.ACTIVE.name());
        strategyVersionDbService.updateById(row);
        return toVO(row);
    }

    @Override
    public StrategyVersionVO disable(Long id) {
        KbPipelineStrategyVersion row = requireById(id);
        row.setStatus(RowStatus.INACTIVE.name());
        strategyVersionDbService.updateById(row);
        return toVO(row);
    }

    @Override
    public boolean delete(Long id) {
        // 策略版本行不可变：有绑定引用禁物理删除（停用代替删除，引用不悬空）
        requireById(id);
        ThrowUtil.throwIf(strategyBindingDbService.existsByStrategyVersionId(id),
                ErrorCode.STRATEGY_BOUND_DELETE_FORBIDDEN);
        return strategyVersionDbService.removeById(id);
    }

    private KbPipelineStrategyVersion requireById(Long id) {
        KbPipelineStrategyVersion row = strategyVersionDbService.getById(id);
        ThrowUtil.throwIf(ObjectUtil.isNull(row), ErrorCode.STRATEGY_VERSION_NOT_FOUND);
        return row;
    }

    /**
     * 策略配置校验：按各环节算法 Spec 校验；非法 40001。
     */
    private String resolveConfigSnapshot(String type, String configSnapshot) {
        validateConfig(type, configSnapshot);
        return configSnapshot;
    }

    private void validateConfig(String type, String configSnapshot) {
        ThrowUtil.throwIf(StrUtil.isBlank(configSnapshot), ErrorCode.PARAM_INVALID, "配置不能为空");
        Map<String, Object> config;
        try {
            config = JsonUtil.toMap(configSnapshot);
        } catch (Exception e) {
            throw new KnowledgeException(ErrorCode.PARAM_INVALID, "配置不是合法 JSON");
        }
        String error = switch (type) {
            case "CHUNK" -> ChunkAlgorithmSpec.validate(config);
            default -> null;
        };
        ThrowUtil.throwIf(error != null, ErrorCode.PARAM_INVALID, error);
    }

    private StrategyVersionVO toVO(KbPipelineStrategyVersion row) {
        StrategyVersionVO vo = new StrategyVersionVO();
        vo.setId(row.getId());
        vo.setType(row.getType());
        vo.setName(row.getName());
        vo.setVersion(row.getVersion());
        vo.setConfigSnapshot(row.getConfigSnapshot());
        vo.setStatus(row.getStatus());
        vo.setCreateTime(row.getCreateTime());
        return vo;
    }
}
