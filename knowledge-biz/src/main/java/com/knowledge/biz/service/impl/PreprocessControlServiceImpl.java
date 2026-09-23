package com.knowledge.biz.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.knowledge.biz.service.PreprocessControlService;
import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineStepLogDbService;
import com.knowledge.biz.service.db.KbPipelineStrategyVersionDbService;
import com.knowledge.biz.service.db.KbStrategyBindingDbService;
import com.knowledge.biz.service.db.KnowledgeBaseDbService;
import com.knowledge.biz.service.support.PreprocessVoAssembler;
import com.knowledge.biz.service.support.TaskDetailSupport;
import com.knowledge.biz.task.TaskTriggerSupport;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineStrategyVersion;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.entity.KbStrategyBinding;
import com.knowledge.common.domain.entity.KnowledgeBase;
import com.knowledge.common.domain.preprocess.PreprocessView;
import com.knowledge.common.domain.rules.KnowledgeBaseRules;
import com.knowledge.common.dto.response.preprocess.PreprocessDetailVO;
import com.knowledge.common.dto.response.preprocess.PreprocessTriggerVO;
import com.knowledge.common.dto.response.task.StageTriggerVO;
import com.knowledge.common.dto.response.task.StepLogVO;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.RowStatus;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.ThrowUtil;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.filecenter.service.FileStorage;
import com.knowledge.worker.preprocessing.strategy.PreprocessStrategy;
import com.knowledge.worker.preprocessing.strategy.PreprocessStrategyParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * 预处理控制面服务实现（手动逐环节）：
 * 策略解析（显式传参 > KB 绑定 > 启用中最新 > 内置默认）→ 上游产物校验 → 防重/唤醒 → 建任务入队。
 * 策略快照写 task.strategy_snapshot（触发时固定），执行只用快照。
 *
 * @author cxxl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PreprocessControlServiceImpl implements PreprocessControlService {

    private final KbFileResultDbService fileResultDbService;
    private final KbPipelineProductDbService pipelineProductDbService;
    private final KbPipelineStepLogDbService stepLogDbService;
    private final KbPipelineStrategyVersionDbService strategyVersionDbService;
    private final KbStrategyBindingDbService strategyBindingDbService;
    private final KnowledgeBaseDbService knowledgeBaseDbService;
    private final TaskTriggerSupport triggerSupport;
    private final TaskDetailSupport detailSupport;
    private final FileStorage fileStorage;
    private final PreprocessStrategyParser strategyParser;
    private final PreprocessVoAssembler voAssembler;

    /**
     * 触发预处理（手动逐环节，重跑同入口）：策略解析 → 上游组装产物校验 → 防重/唤醒 → 新建 PREPROCESS 任务入队。
     *
     * @param fileResultId      文件结果 ID
     * @param strategyVersionId 策略版本行 ID（可选，缺省取库内启用中最新，库内无回退内置默认）
     * @param upstreamProductId 上游组装产物 ID（可选，缺省取最新）
     * @return 触发响应（任务 ID + 生效策略版本）
     */
    @Override
    public PreprocessTriggerVO preprocess(Long fileResultId, Long strategyVersionId, Long upstreamProductId) {
        KbFileResult fileResult = fileResultDbService.getById(fileResultId);
        ThrowUtil.throwIf(ObjectUtil.isNull(fileResult), ErrorCode.FILE_RESULT_NOT_FOUND);
        PreprocessStrategy strategy = resolveStrategy(fileResult, strategyVersionId);

        // 可选指定上游组装产物；缺省取最新
        KbPipelineProduct structureProduct = requireStructureProduct(fileResultId, upstreamProductId);
        ThrowUtil.throwIf(ObjectUtil.isNull(structureProduct), ErrorCode.FILE_RESULT_NOT_FOUND,
                "统一结构产物不存在，请先触发组装");

        StageTriggerVO triggered = triggerSupport.trigger(fileResultId, PipelineStage.PREPROCESS,
                structureProduct.getId(), JsonUtil.toJsonStr(strategy), "预处理", false);
        return new PreprocessTriggerVO(fileResultId, triggered.getPipelineTaskId(), strategy.fullVersion());
    }

    @Override
    public PreprocessDetailVO preprocessDetail(Long fileResultId, Long taskId) {
        KbFileResult fileResult = fileResultDbService.getById(fileResultId);
        ThrowUtil.throwIf(ObjectUtil.isNull(fileResult), ErrorCode.FILE_RESULT_NOT_FOUND);
        KbPipelineTask task = detailSupport.resolveTask(fileResultId, PipelineStage.PREPROCESS, taskId, "预处理");

        PreprocessDetailVO vo = new PreprocessDetailVO();
        vo.setFileResultId(fileResultId);
        if (ObjectUtil.isNotNull(task)) {
            vo.applyFrom(task);
            vo.setSteps(stepLogDbService.listByTaskId(task.getId()).stream().map(StepLogVO::of).toList());
        }

        // 产物引用/统计/视图元素：按 task.productId 精确取该次运行的产物（历史任务同样可展示自己的产物；无任务/无产物留空）
        vo.setElements(new ArrayList<>());
        KbPipelineProduct product = ObjectUtil.isNull(task) || task.getProductId() == null ? null
                : pipelineProductDbService.getById(task.getProductId());
        if (ObjectUtil.isNotNull(product)) {
            vo.setArtifactId(product.getArtifactId());
            vo.setContentHash(product.getContentHash());
            vo.setCapabilitySnapshot(product.getCapabilitySnapshot());
            readView(product.getArtifactId(), vo);
        }
        return vo;
    }

    /** 读派生视图产物并委托 VO 组装器提取统计/视图元素（读取失败记日志并留空，不阻断详情） */
    private void readView(String artifactId, PreprocessDetailVO vo) {
        try {
            byte[] content = fileStorage.getObject(artifactId);
            PreprocessView view = JsonUtil.toObject(
                    new String(content, StandardCharsets.UTF_8), PreprocessView.class);
            if (ObjectUtil.isNull(view)) {
                log.debug("预处理视图产物反序列化为空, artifactId={}", artifactId);
                return;
            }
            vo.setSummary(voAssembler.toSummary(view));
            vo.setElements(voAssembler.toElementVOs(view));
        } catch (Exception e) {
            log.warn("读取预处理视图产物失败, artifactId={}", artifactId, e);
        }
    }

    /** 策略解析四档：显式指定（40433 校验存在/类型/启用）→ KB 绑定（开关开启时，失效回退告警）→ 启用中最新 → 内置默认。 */
    private PreprocessStrategy resolveStrategy(KbFileResult fileResult, Long strategyVersionId) {
        if (ObjectUtil.isNotNull(strategyVersionId)) {
            // 显式指定策略：按行 id 精确引用
            KbPipelineStrategyVersion row = strategyVersionDbService.getById(strategyVersionId);
            ThrowUtil.throwIf(ObjectUtil.isNull(row), ErrorCode.STRATEGY_VERSION_NOT_FOUND);
            ThrowUtil.throwIf(!PreprocessStrategy.TYPE.equals(row.getType()),
                    ErrorCode.STRATEGY_VERSION_NOT_FOUND, "策略类型不匹配：期望 " + PreprocessStrategy.TYPE);
            ThrowUtil.throwIf(!RowStatus.ACTIVE.name().equals(row.getStatus()),
                    ErrorCode.STRATEGY_VERSION_NOT_FOUND, "策略已停用，请先启用后再触发");
            return toStrategy(row);
        }
        // KB 绑定档位：绑定开关开启且存在有效绑定则用之；绑定行失效（行缺失/停用）回退下一档并告警
        KnowledgeBase kb = knowledgeBaseDbService.getActiveById(fileResult.getKnowledgeBaseId());
        if (KnowledgeBaseRules.isStrategyBindingEnabled(kb)) {
            KbStrategyBinding binding = strategyBindingDbService
                    .getByKbAndType(fileResult.getKnowledgeBaseId(), PreprocessStrategy.TYPE);
            if (ObjectUtil.isNotNull(binding)) {
                KbPipelineStrategyVersion bound = strategyVersionDbService.getById(binding.getStrategyVersionId());
                if (ObjectUtil.isNotNull(bound) && RowStatus.ACTIVE.name().equals(bound.getStatus())) {
                    return toStrategy(bound);
                }
                log.warn("===> PreprocessControlServiceImpl 预处理 KB 绑定策略失效，回退全局最新启用, fileResultId={}, bindingId={}",
                        fileResult.getId(), binding.getId());
            }
        }
        KbPipelineStrategyVersion latest = strategyVersionDbService.getLatestEnabledByType(PreprocessStrategy.TYPE);
        return ObjectUtil.isNull(latest) ? strategyParser.defaultStrategy() : toStrategy(latest);
    }

    /** 上游组装产物校验：指定 id 则校验存在/环节/归属；缺省取该文件结果最新 STRUCTURE 产物。 */
    private KbPipelineProduct requireStructureProduct(Long fileResultId, Long productId) {
        if (ObjectUtil.isNull(productId)) {
            return pipelineProductDbService.getByFileResultIdAndStage(fileResultId, PipelineStage.STRUCTURE.name());
        }
        KbPipelineProduct product = pipelineProductDbService.getById(productId);
        ThrowUtil.throwIf(ObjectUtil.isNull(product), ErrorCode.FILE_RESULT_NOT_FOUND, "指定上游产物不存在");
        ThrowUtil.throwIf(!PipelineStage.STRUCTURE.name().equals(product.getStage()), ErrorCode.FILE_RESULT_NOT_FOUND,
                "指定产物环节不匹配：期望 STRUCTURE");
        ThrowUtil.throwIf(!fileResultId.equals(product.getFileResultId()), ErrorCode.FILE_RESULT_NOT_FOUND,
                "指定产物不属于该文件结果");
        return product;
    }

    private PreprocessStrategy toStrategy(KbPipelineStrategyVersion row) {
        // configSnapshot 为 rules/custom 结构（无 name/version），解析补全默认后回填行信息
        PreprocessStrategy strategy = strategyParser.parseConfig(row.getConfigSnapshot());
        strategy.setType(row.getType());
        strategy.setName(row.getName());
        strategy.setVersion(row.getVersion());
        return strategy;
    }
}
