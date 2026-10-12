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
 * 环节策略解析共用助手：显式指定 → KB 绑定两档口径完全同构，集中在这里；
 * 两档都不命中一律拒绝（不做「取启用中最新」这类兜底，也不在代码里造默认策略）。
 *
 * @author cxxl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StageStrategySupport {

    private final KbPipelineStrategyVersionDbService strategyVersionDbService;
    private final KbStrategyBindingDbService strategyBindingDbService;
    private final KnowledgeBaseDbService knowledgeBaseDbService;

    /**
     * 解析生效策略版本行：显式指定优先，否则取知识库绑定；两档都不命中（绑定开关关闭、未绑定、
     * 绑定行缺失或已停用）一律拒绝 40438，不做兜底。
     *
     * @param fileResult        文件结果（取所属知识库判定绑定开关）
     * @param strategyVersionId 显式指定的策略版本行 ID（可空）
     * @param type              策略类型（PREPROCESS / CHUNK / EMBED）
     * @param stageLabel        环节中文名（失败文案用）
     * @return 生效的策略版本行（非空）
     */
    public KbPipelineStrategyVersion resolve(KbFileResult fileResult, Long strategyVersionId,
                                             String type, String stageLabel) {
        if (NullUtil.isNotNull(strategyVersionId)) {
            return requireExplicit(strategyVersionId, type);
        }
        KnowledgeBase kb = knowledgeBaseDbService.getActiveById(fileResult.getKnowledgeBaseId());
        ThrowUtil.throwIf(!KnowledgeBaseRules.isStrategyBindingEnabled(kb), ErrorCode.TASK_STRATEGY_MISSING,
                stageLabel + "未绑定策略，请显式选择策略后触发");
        KbStrategyBinding binding = strategyBindingDbService
                .getByKbAndType(fileResult.getKnowledgeBaseId(), type);
        ThrowUtil.throwIf(NullUtil.isNull(binding), ErrorCode.TASK_STRATEGY_MISSING,
                stageLabel + "未绑定策略，请显式选择策略后触发");
        KbPipelineStrategyVersion bound = strategyVersionDbService.getById(binding.getStrategyVersionId());
        ThrowUtil.throwIf(NullUtil.isNull(bound) || !RowStatus.ACTIVE.name().equals(bound.getStatus()),
                ErrorCode.TASK_STRATEGY_MISSING, stageLabel + "绑定的策略已停用或不存在，请重新绑定或显式选择策略");
        return bound;
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
