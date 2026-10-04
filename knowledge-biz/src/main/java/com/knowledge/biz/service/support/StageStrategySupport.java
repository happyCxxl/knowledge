package com.knowledge.biz.service.support;

import com.knowledge.biz.service.db.KbPipelineStrategyVersionDbService;
import com.knowledge.biz.service.db.KbStrategyBindingDbService;
import com.knowledge.biz.service.db.KnowledgeBaseDbService;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineStrategyVersion;
import com.knowledge.common.domain.entity.KbStrategyBinding;
import com.knowledge.common.domain.entity.KnowledgeBase;
import com.knowledge.common.domain.rules.KnowledgeBaseRules;
import com.knowledge.common.enums.task.RowStatus;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.ThrowUtil;
import com.knowledge.common.utils.NullUtil;
import com.knowledge.worker.StageStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 环节策略解析共用助手：各环节"显式指定 → KB 绑定档位 → 启用中最新 → 内置默认"四档口径完全同构，
 * 集中在这里；各控制服务只保留自己的策略解析器与内置默认策略。
 *
 * @author cxxl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StageStrategySupport {

    /** KB 绑定策略失效（行缺失或已停用）时回退下一档，并写告警（含环节名、文件结果 ID、绑定行 ID） */
    private static final String BINDING_INVALID_LOG =
            "===> {} KB 绑定策略失效，回退全局最新启用, fileResultId={}, bindingId={}";

    private final KbPipelineStrategyVersionDbService strategyVersionDbService;
    private final KbStrategyBindingDbService strategyBindingDbService;
    private final KnowledgeBaseDbService knowledgeBaseDbService;

    /**
     * 解析生效策略版本行（前三档）：显式指定 → KB 绑定 → 启用中最新。
     * 全不命中返回 null。
     *
     * @param fileResult       文件结果（取所属知识库判定绑定开关）
     * @param strategyVersionId 显式指定的策略版本行 ID（可空）
     * @param type             策略类型（CHUNK / EMBED / PREPROCESS）
     * @param stageLabel       环节中文名（告警文案用）
     */
    public KbPipelineStrategyVersion resolve(KbFileResult fileResult, Long strategyVersionId,
                                             String type, String stageLabel) {
        if (NullUtil.isNotNull(strategyVersionId)) {
            return requireExplicit(strategyVersionId, type);
        }
        KbPipelineStrategyVersion bound = resolveBound(fileResult, type, stageLabel);
        return NullUtil.isNotNull(bound) ? bound : strategyVersionDbService.getLatestEnabledByType(type);
    }

    /**
     * 显式指定档：按行 id 精确引用，校验存在、类型匹配、处于启用（任一不满足报 40433）。
     *
     * @param strategyVersionId 策略版本行 ID（非空）
     * @param type              期望的策略类型
     */
    public KbPipelineStrategyVersion requireExplicit(Long strategyVersionId, String type) {
        KbPipelineStrategyVersion row = strategyVersionDbService.getById(strategyVersionId);
        ThrowUtil.throwIf(NullUtil.isNull(row), ErrorCode.STRATEGY_VERSION_NOT_FOUND);
        ThrowUtil.throwIf(!type.equals(row.getType()),
                ErrorCode.STRATEGY_VERSION_NOT_FOUND, "策略类型不匹配：期望 " + type);
        ThrowUtil.throwIf(!RowStatus.ACTIVE.name().equals(row.getStatus()),
                ErrorCode.STRATEGY_VERSION_NOT_FOUND, "策略已停用，请先启用后再触发");
        return row;
    }

    /**
     * KB 绑定档：绑定开关开启且绑定行有效（存在且启用）则用之；
     * 未绑定、绑定行缺失或已停用一律返回 null，失效时写告警。
     *
     * @param fileResult 文件结果（取所属知识库）
     * @param type       策略类型
     * @param stageLabel 环节中文名（告警文案用）
     */
    public KbPipelineStrategyVersion resolveBound(KbFileResult fileResult, String type, String stageLabel) {
        KnowledgeBase kb = knowledgeBaseDbService.getActiveById(fileResult.getKnowledgeBaseId());
        if (!KnowledgeBaseRules.isStrategyBindingEnabled(kb)) {
            return null;
        }
        KbStrategyBinding binding = strategyBindingDbService
                .getByKbAndType(fileResult.getKnowledgeBaseId(), type);
        if (NullUtil.isNull(binding)) {
            return null;
        }
        KbPipelineStrategyVersion bound = strategyVersionDbService.getById(binding.getStrategyVersionId());
        if (NullUtil.isNotNull(bound) && RowStatus.ACTIVE.name().equals(bound.getStatus())) {
            return bound;
        }
        log.warn(BINDING_INVALID_LOG, stageLabel, fileResult.getId(), binding.getId());
        return null;
    }

    /**
     * 按策略版本行回填元信息：策略配置快照（configSnapshot）里没有 type/name/version 三项，
     * 统一在这里补齐，各环节解析器不必各自重复一遍 setter。
     *
     * @param strategy 环节策略实例（解析器已按快照构造）
     * @param row      策略版本行
     * @return 传入的策略实例（type/name/version 已回填）
     */
    public <T extends StageStrategy> T bindMeta(T strategy, KbPipelineStrategyVersion row) {
        strategy.setType(row.getType());
        strategy.setName(row.getName());
        strategy.setVersion(row.getVersion());
        return strategy;
    }
}
