package com.knowledge.biz.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.biz.service.EmbedControlService;
import com.knowledge.biz.service.db.KbEmbeddingRecordDbService;
import com.knowledge.biz.service.db.KbEmbeddingSetDbService;
import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineStepLogDbService;
import com.knowledge.biz.service.db.KbPipelineStrategyVersionDbService;
import com.knowledge.biz.service.db.KbStrategyBindingDbService;
import com.knowledge.biz.service.db.KnowledgeBaseDbService;
import com.knowledge.biz.service.support.EmbedVoAssembler;
import com.knowledge.biz.service.support.TaskDetailSupport;
import com.knowledge.biz.task.TaskTriggerSupport;
import com.knowledge.common.domain.entity.KbEmbeddingRecord;
import com.knowledge.common.domain.entity.KbEmbeddingSet;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineStrategyVersion;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.entity.KbStrategyBinding;
import com.knowledge.common.domain.entity.KnowledgeBase;
import com.knowledge.common.domain.rules.KnowledgeBaseRules;
import com.knowledge.common.dto.response.embed.EmbedDetailVO;
import com.knowledge.common.dto.response.embed.EmbedTriggerVO;
import com.knowledge.common.dto.response.task.StageTriggerVO;
import com.knowledge.common.dto.response.task.StepLogVO;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.RowStatus;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.ThrowUtil;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.worker.chunking.ChunkProperties;
import com.knowledge.worker.chunking.strategy.ChunkStrategy;
import com.knowledge.worker.chunking.strategy.ChunkStrategyParser;
import com.knowledge.worker.embedding.EmbedProperties;
import com.knowledge.worker.embedding.EmbedWindowRules;
import com.knowledge.worker.embedding.strategy.EmbedStrategy;
import com.knowledge.worker.embedding.strategy.EmbedStrategyParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量化控制面服务实现（手动逐环节，与切片/预处理控制面同构）：
 * 策略解析（传参校验 / KB 绑定 / 全局最新启用 / 内置默认）→ 上游 CHUNK 产物校验 →
 * 窗口前置校验（不兜底，触发时明确报错）→ 防重复用 → 建任务入队。
 *
 * @author cxxl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbedControlServiceImpl implements EmbedControlService {

    private final KbFileResultDbService fileResultDbService;
    private final KnowledgeBaseDbService knowledgeBaseDbService;
    private final KbPipelineProductDbService pipelineProductDbService;
    private final KbPipelineStrategyVersionDbService strategyVersionDbService;
    private final KbPipelineStepLogDbService stepLogDbService;
    private final KbEmbeddingSetDbService embeddingSetDbService;
    private final KbEmbeddingRecordDbService embeddingRecordDbService;
    private final EmbedVoAssembler voAssembler;
    private final EmbedStrategyParser strategyParser;
    private final ChunkStrategyParser chunkStrategyParser;
    private final EmbedProperties embedProperties;
    private final ChunkProperties chunkProperties;
    private final KbStrategyBindingDbService strategyBindingDbService;
    private final TaskTriggerSupport triggerSupport;
    private final TaskDetailSupport detailSupport;

    @Override
    public EmbedTriggerVO embed(Long fileResultId, Long strategyVersionId, Long upstreamProductId) {
        KbFileResult fileResult = fileResultDbService.getById(fileResultId);
        ThrowUtil.throwIf(ObjectUtil.isNull(fileResult), ErrorCode.FILE_RESULT_NOT_FOUND);
        EmbedStrategy strategy = resolveStrategy(fileResult, strategyVersionId);

        // 上游切片产物：可选指定，缺省取最新
        KbPipelineProduct chunkProduct = requireChunkProduct(fileResultId, upstreamProductId);
        ThrowUtil.throwIf(ObjectUtil.isNull(chunkProduct), ErrorCode.EMBED_UPSTREAM_MISSING);

        // 窗口前置校验：切片策略最大片长 ≤ 模型窗口（不兼容即报错，不做兜底）
        precheckWindow(chunkProduct, strategy);

        StageTriggerVO triggered = triggerSupport.trigger(fileResultId, PipelineStage.EMBED,
                chunkProduct.getId(), JsonUtil.toJsonStr(strategy), "向量化", false);
        return new EmbedTriggerVO(fileResultId, triggered.getPipelineTaskId(), strategy.fullVersion());
    }

    @Override
    public EmbedDetailVO embedDetail(Long fileResultId, Long taskId) {
        KbFileResult fileResult = fileResultDbService.getById(fileResultId);
        ThrowUtil.throwIf(ObjectUtil.isNull(fileResult), ErrorCode.FILE_RESULT_NOT_FOUND);
        KbPipelineTask task = detailSupport.resolveTask(fileResultId, PipelineStage.EMBED, taskId, "向量化");

        EmbedDetailVO vo = new EmbedDetailVO();
        vo.setFileResultId(fileResultId);
        vo.setRecords(new ArrayList<>());
        if (ObjectUtil.isNotNull(task)) {
            vo.setTaskId(task.getId());
            vo.setStage(task.getStage());
            vo.setTaskStatus(task.getStatus());
            vo.setErrorCode(task.getErrorCode());
            vo.setErrorMsg(task.getErrorMsg());
            vo.setStartedAt(task.getStartedAt());
            vo.setFinishedAt(task.getFinishedAt());
            vo.setSteps(stepLogDbService.listByTaskId(task.getId()).stream().map(StepLogVO::of).toList());
        }

        // 集合摘要/记录：按 task.productId → 产物 → artifactId 精确取该次运行的集合（历史任务同样可展示自己的集合；无任务/无产物留空）
        KbPipelineProduct product = ObjectUtil.isNull(task) || task.getProductId() == null ? null
                : pipelineProductDbService.getById(task.getProductId());
        if (ObjectUtil.isNotNull(product)) {
            KbEmbeddingSet embeddingSet = embeddingSetDbService.getByArtifactId(product.getArtifactId());
            if (ObjectUtil.isNotNull(embeddingSet)) {
                List<KbEmbeddingRecord> records = embeddingRecordDbService.listByEmbeddingSetId(embeddingSet.getId());
                vo.setSummary(voAssembler.toSummary(embeddingSet, records));
                vo.setRecords(voAssembler.toRecordItemVOs(records));
            }
            vo.setArtifactId(product.getArtifactId());
            vo.setContentHash(product.getContentHash());
            vo.setCapabilitySnapshot(product.getCapabilitySnapshot());
        }
        return vo;
    }

    /** 窗口前置校验（触发时明确报错；不可推算时跳过，任务期告警） */
    private void precheckWindow(KbPipelineProduct chunkProduct, EmbedStrategy strategy) {
        ChunkStrategy chunkStrategy = resolveChunkStrategy(chunkProduct.getCapabilitySnapshot());
        if (chunkStrategy == null) {
            return;
        }
        EmbedWindowRules.WindowBound window = EmbedWindowRules.resolve(
                chunkStrategy, chunkProperties, strategy, embedProperties);
        if (!window.computable()) {
            return; // 兜底 none 不可推算：跳过（评测专用口径）
        }
        ThrowUtil.throwIf(window.bound() > window.allowed(), ErrorCode.EMBED_MODEL_INCOMPATIBLE,
                "切片策略最大片长 " + window.bound() + " 字符超过模型窗口 " + window.allowed() + " 字符，请更换模型或调整切片策略");
    }

    /** 上游切片产物校验：指定 id 则校验存在/环节/归属；缺省取该文件结果最新 CHUNK 产物。 */
    private KbPipelineProduct requireChunkProduct(Long fileResultId, Long productId) {
        if (ObjectUtil.isNull(productId)) {
            return pipelineProductDbService.getByFileResultIdAndStage(fileResultId, PipelineStage.CHUNK.name());
        }
        KbPipelineProduct product = pipelineProductDbService.getById(productId);
        ThrowUtil.throwIf(ObjectUtil.isNull(product), ErrorCode.FILE_RESULT_NOT_FOUND, "指定上游产物不存在");
        ThrowUtil.throwIf(!PipelineStage.CHUNK.name().equals(product.getStage()), ErrorCode.FILE_RESULT_NOT_FOUND,
                "指定产物环节不匹配：期望 CHUNK");
        ThrowUtil.throwIf(!fileResultId.equals(product.getFileResultId()), ErrorCode.FILE_RESULT_NOT_FOUND,
                "指定产物不属于该文件结果");
        return product;
    }

    private ChunkStrategy resolveChunkStrategy(String snapshot) {
        if (StrUtil.isBlank(snapshot)) {
            return null;
        }
        return chunkStrategyParser.parse(snapshot);
    }

    private EmbedStrategy resolveStrategy(KbFileResult fileResult, Long strategyVersionId) {
        if (ObjectUtil.isNotNull(strategyVersionId)) {
            // 显式指定策略：按行 id 精确引用
            KbPipelineStrategyVersion row = strategyVersionDbService.getById(strategyVersionId);
            ThrowUtil.throwIf(ObjectUtil.isNull(row), ErrorCode.STRATEGY_VERSION_NOT_FOUND);
            ThrowUtil.throwIf(!EmbedStrategy.TYPE.equals(row.getType()),
                    ErrorCode.STRATEGY_VERSION_NOT_FOUND, "策略类型不匹配：期望 " + EmbedStrategy.TYPE);
            ThrowUtil.throwIf(!RowStatus.ACTIVE.name().equals(row.getStatus()),
                    ErrorCode.STRATEGY_VERSION_NOT_FOUND, "策略已停用，请先启用后再触发");
            return toStrategy(row);
        }
        // 知识库绑定策略优先于全局最新启用（与 CHUNK/PREPROCESS 同构）；
        // 知识库关闭策略绑定（测评模式）时跳过绑定档，必须显式选策略
        KnowledgeBase kb = knowledgeBaseDbService.getById(fileResult.getKnowledgeBaseId());
        if (KnowledgeBaseRules.isStrategyBindingEnabled(kb)) {
            KbStrategyBinding binding = strategyBindingDbService
                    .getByKbAndType(fileResult.getKnowledgeBaseId(), EmbedStrategy.TYPE);
            if (ObjectUtil.isNotNull(binding)) {
                KbPipelineStrategyVersion bound = strategyVersionDbService.getById(binding.getStrategyVersionId());
                if (ObjectUtil.isNotNull(bound) && RowStatus.ACTIVE.name().equals(bound.getStatus())) {
                    return toStrategy(bound);
                }
                log.warn("===> EmbedControlServiceImpl 知识库绑定向量策略失效, 回退全局最新启用, kbId={}, versionId={}",
                        fileResult.getKnowledgeBaseId(), binding.getStrategyVersionId());
            }
        }
        KbPipelineStrategyVersion latest = strategyVersionDbService.getLatestEnabledByType(EmbedStrategy.TYPE);
        return ObjectUtil.isNull(latest) ? strategyParser.defaultStrategy() : toStrategy(latest);
    }

    private EmbedStrategy toStrategy(KbPipelineStrategyVersion row) {
        EmbedStrategy strategy = strategyParser.parse(row.getConfigSnapshot());
        strategy.setType(row.getType());
        strategy.setName(row.getName());
        strategy.setVersion(row.getVersion());
        return strategy;
    }
}
