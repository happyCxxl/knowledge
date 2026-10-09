package com.knowledge.biz.service.impl;

import com.knowledge.biz.service.PreprocessControlService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.support.PreprocessStatsSupport;
import com.knowledge.biz.service.support.PreprocessVoAssembler;
import com.knowledge.biz.service.support.StageStrategySupport;
import com.knowledge.biz.service.support.StatsSupport;
import com.knowledge.biz.service.support.FileResultAccessGuard;
import com.knowledge.biz.service.support.TaskDetailSupport;
import com.knowledge.biz.task.TaskTriggerSupport;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineStrategyVersion;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.preprocess.PreprocessView;
import com.knowledge.common.domain.storage.ObjectRef;
import com.knowledge.common.dto.response.preprocess.PreprocessDetailVO;
import com.knowledge.common.dto.response.preprocess.PreprocessTriggerVO;
import com.knowledge.common.dto.response.task.StageTriggerVO;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import com.knowledge.common.exception.ThrowUtil;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.common.utils.NullUtil;
import com.knowledge.filecenter.service.FileStorage;
import com.knowledge.worker.preprocessing.strategy.PreprocessStrategy;
import com.knowledge.worker.preprocessing.strategy.PreprocessStrategyParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Map;

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

    private final KbPipelineProductDbService pipelineProductDbService;
    private final StageStrategySupport strategySupport;
    private final TaskTriggerSupport triggerSupport;
    private final TaskDetailSupport detailSupport;
    private final FileStorage fileStorage;
    private final PreprocessStrategyParser strategyParser;
    private final PreprocessVoAssembler voAssembler;
    private final FileResultAccessGuard accessGuard;

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
        KbFileResult fileResult = accessGuard.requireExisting(fileResultId);
        PreprocessStrategy strategy = resolveStrategy(fileResult, strategyVersionId);

        // 可选指定上游组装产物；缺省取最新
        KbPipelineProduct structureProduct = requireStructureProduct(fileResultId, upstreamProductId);
        ThrowUtil.throwIf(NullUtil.isNull(structureProduct), ErrorCode.FILE_RESULT_NOT_FOUND,
                "统一结构产物不存在，请先触发组装");

        StageTriggerVO triggered = triggerSupport.trigger(fileResultId, PipelineStage.PREPROCESS,
                structureProduct.getId(), JsonUtil.toJsonStr(strategy), "预处理", false);
        return new PreprocessTriggerVO(fileResultId, triggered.getPipelineTaskId(), strategy.fullVersion());
    }

    @Override
    public PreprocessDetailVO preprocessDetail(Long fileResultId, Long taskId) {
        accessGuard.requireExisting(fileResultId);
        KbPipelineTask task = detailSupport.resolveTask(fileResultId, PipelineStage.PREPROCESS, taskId, "预处理");

        PreprocessDetailVO vo = new PreprocessDetailVO();
        detailSupport.withTask(vo, fileResultId, task);

        // 产物引用/统计/视图元素：按 task.productId 精确取该次运行的产物（历史任务同样可展示自己的产物；无任务/无产物留空）
        vo.setElements(new ArrayList<>());
        PreprocessView view = null;
        KbPipelineProduct product = detailSupport.productOfTask(task);
        if (NullUtil.isNotNull(product)) {
            detailSupport.withProductRef(vo, product);
            view = readView(product, vo);
        }
        // 统计与摘要读产物现算：与执行树预处理节点同一份口径，不落产物；产物读不到时不陈述结论
        Map<String, Object> stageStats = PreprocessStatsSupport.stats(vo.getStartedAt(), vo.getFinishedAt(), view);
        vo.setStageStats(stageStats);
        vo.setStageSummary(PreprocessStatsSupport.summary(vo.getErrorMsg(), stageStats, vo.getStatus()));
        return vo;
    }

    /**
     * 读派生视图产物并委托 VO 组装器提取统计/视图元素。
     *
     * <p>存储类读取失败（40454 / 40455）向上抛出；其余读取失败记日志并留空，不阻断详情。
     *
     * @return 产物本体；读不到返回 null（统计与摘要随之为空）
     */
    private PreprocessView readView(KbPipelineProduct product, PreprocessDetailVO vo) {
        try {
            byte[] content = fileStorage.getObject(ObjectRef.ofProduct(product));
            PreprocessView view = JsonUtil.toObject(
                    new String(content, StandardCharsets.UTF_8), PreprocessView.class);
            if (NullUtil.isNull(view)) {
                log.debug("预处理视图产物反序列化为空, artifactId={}", product.getArtifactId());
                return null;
            }
            vo.setSummary(voAssembler.toSummary(view));
            vo.setElements(voAssembler.toElementVOs(view));
            return view;
        } catch (Exception e) {
            KnowledgeException storageFailure = StatsSupport.storageFailureOf(e);
            if (NullUtil.isNotNull(storageFailure)) {
                throw storageFailure;
            }
            log.warn("读取预处理视图产物失败, artifactId={}", product.getArtifactId(), e);
            return null;
        }
    }

    /** 策略解析四档：显式指定（40433 校验存在/类型/启用）→ KB 绑定（开关开启时，失效回退告警）→ 启用中最新 → 内置默认。 */
    private PreprocessStrategy resolveStrategy(KbFileResult fileResult, Long strategyVersionId) {
        KbPipelineStrategyVersion row = strategySupport.resolve(
                fileResult, strategyVersionId, PreprocessStrategy.TYPE, "PreprocessControlServiceImpl 预处理");
        return NullUtil.isNull(row) ? strategyParser.defaultStrategy() : toStrategy(row);
    }

    /** 上游组装产物校验：指定 id 则校验存在/环节/归属；缺省取该文件结果最新 STRUCTURE 产物。 */
    private KbPipelineProduct requireStructureProduct(Long fileResultId, Long productId) {
        if (NullUtil.isNull(productId)) {
            return pipelineProductDbService.getByFileResultIdAndStage(fileResultId, PipelineStage.STRUCTURE.name());
        }
        KbPipelineProduct product = pipelineProductDbService.getById(productId);
        ThrowUtil.throwIf(NullUtil.isNull(product), ErrorCode.FILE_RESULT_NOT_FOUND, "指定上游产物不存在");
        ThrowUtil.throwIf(!PipelineStage.STRUCTURE.name().equals(product.getStage()), ErrorCode.FILE_RESULT_NOT_FOUND,
                "指定产物环节不匹配：期望 STRUCTURE");
        ThrowUtil.throwIf(!fileResultId.equals(product.getFileResultId()), ErrorCode.FILE_RESULT_NOT_FOUND,
                "指定产物不属于该文件结果");
        return product;
    }

    private PreprocessStrategy toStrategy(KbPipelineStrategyVersion row) {
        // configSnapshot 为 rules/custom 结构（无 name/version），解析补全默认后回填行信息
        return strategySupport.bindMeta(strategyParser.parseConfig(row.getConfigSnapshot()), row);
    }
}
