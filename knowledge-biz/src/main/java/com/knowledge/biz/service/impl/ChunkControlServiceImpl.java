package com.knowledge.biz.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.knowledge.biz.service.ChunkControlService;
import com.knowledge.biz.service.db.KbChunkDbService;
import com.knowledge.biz.service.db.KbChunkSetDbService;
import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineStepLogDbService;
import com.knowledge.biz.service.db.KbPipelineStrategyVersionDbService;
import com.knowledge.biz.service.support.ChunkVoAssembler;
import com.knowledge.biz.service.support.TaskDetailSupport;
import com.knowledge.biz.task.TaskTriggerSupport;
import com.knowledge.common.domain.entity.KbChunk;
import com.knowledge.common.domain.entity.KbChunkSet;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineStrategyVersion;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.rules.ChunkRules;
import com.knowledge.common.dto.response.chunk.ChunkDetailVO;
import com.knowledge.common.dto.response.chunk.ChunkTriggerVO;
import com.knowledge.common.dto.response.task.StageTriggerVO;
import com.knowledge.common.dto.response.task.StepLogVO;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.RowStatus;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.ThrowUtil;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.worker.chunking.strategy.ChunkStrategy;
import com.knowledge.worker.chunking.strategy.ChunkStrategyParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 切片控制面服务实现（手动逐环节）：
 * 策略解析（传参校验 / 缺省启用中最新 / 库内无回退内置默认）→ 上游产物校验 → 防重/唤醒 → 建任务入队。
 * 策略快照写 task.strategy_snapshot（触发时固定），执行只用快照。
 *
 * @author cxxl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChunkControlServiceImpl implements ChunkControlService {

    private final KbFileResultDbService fileResultDbService;
    private final KbPipelineProductDbService pipelineProductDbService;
    private final KbPipelineStepLogDbService stepLogDbService;
    private final KbPipelineStrategyVersionDbService strategyVersionDbService;
    private final TaskTriggerSupport triggerSupport;
    private final TaskDetailSupport detailSupport;
    private final KbChunkSetDbService chunkSetDbService;
    private final KbChunkDbService chunkDbService;
    private final ChunkStrategyParser strategyParser;
    private final ChunkVoAssembler voAssembler;

    /**
     * 触发切片（手动逐环节，重跑同入口）：策略解析 → 上游预处理视图产物校验 → 防重/唤醒 → 新建 CHUNK 任务入队。
     *
     * @param fileResultId      文件结果 ID
     * @param strategyVersionId 策略版本行 ID（可选，缺省取库内启用中最新，库内无回退内置默认）
     * @param upstreamProductId 上游预处理产物 ID（可选，缺省取最新）
     * @return 触发响应（任务 ID + 生效策略版本）
     */
    @Override
    public ChunkTriggerVO chunk(Long fileResultId, Long strategyVersionId, Long upstreamProductId) {
        KbFileResult fileResult = fileResultDbService.getById(fileResultId);
        ThrowUtil.throwIf(ObjectUtil.isNull(fileResult), ErrorCode.FILE_RESULT_NOT_FOUND);
        ChunkStrategy strategy = resolveStrategy(strategyVersionId);

        // 可选指定上游预处理产物；缺省取最新
        KbPipelineProduct preprocessProduct = requirePreprocessProduct(fileResultId, upstreamProductId);
        ThrowUtil.throwIf(ObjectUtil.isNull(preprocessProduct), ErrorCode.FILE_RESULT_NOT_FOUND,
                ChunkRules.UPSTREAM_PREPROCESS_MISSING);

        StageTriggerVO triggered = triggerSupport.trigger(fileResultId, PipelineStage.CHUNK,
                preprocessProduct.getId(), JsonUtil.toJsonStr(strategy), "切片", false);
        return new ChunkTriggerVO(fileResultId, triggered.getPipelineTaskId(), strategy.fullVersion());
    }

    @Override
    public ChunkDetailVO chunkDetail(Long fileResultId, Long taskId) {
        KbFileResult fileResult = fileResultDbService.getById(fileResultId);
        ThrowUtil.throwIf(ObjectUtil.isNull(fileResult), ErrorCode.FILE_RESULT_NOT_FOUND);
        KbPipelineTask task = detailSupport.resolveTask(fileResultId, PipelineStage.CHUNK, taskId, "切片");

        ChunkDetailVO vo = new ChunkDetailVO();
        vo.setFileResultId(fileResultId);
        if (ObjectUtil.isNotNull(task)) {
            vo.applyFrom(task);
            vo.setSteps(stepLogDbService.listByTaskId(task.getId()).stream().map(StepLogVO::of).toList());
        }

        // 切片集合/切片列表：按 task.productId → 产物 → artifactId 精确取该次运行的集合（历史任务同样可展示自己的集合；无任务/无产物留空）
        vo.setChunks(new ArrayList<>());
        KbPipelineProduct product = ObjectUtil.isNull(task) || task.getProductId() == null ? null
                : pipelineProductDbService.getById(task.getProductId());
        if (ObjectUtil.isNotNull(product)) {
            KbChunkSet chunkSet = chunkSetDbService.getByArtifactId(product.getArtifactId());
            if (ObjectUtil.isNotNull(chunkSet)) {
                List<KbChunk> chunks = chunkDbService.listByChunkSetId(chunkSet.getId());
                vo.setSummary(voAssembler.toSummary(chunkSet, chunks));
                vo.setChunks(voAssembler.toChunkItemVOs(chunks));
            }
            vo.setArtifactId(product.getArtifactId());
            vo.setContentHash(product.getContentHash());
            vo.setCapabilitySnapshot(product.getCapabilitySnapshot());
        }
        return vo;
    }

    /** 策略解析三档：显式指定（40433 校验存在/类型/启用）→ 启用中最新 → 内置默认。 */
    private ChunkStrategy resolveStrategy(Long strategyVersionId) {
        if (ObjectUtil.isNotNull(strategyVersionId)) {
            // 显式指定策略：按行 id 精确引用
            KbPipelineStrategyVersion row = strategyVersionDbService.getById(strategyVersionId);
            ThrowUtil.throwIf(ObjectUtil.isNull(row), ErrorCode.STRATEGY_VERSION_NOT_FOUND);
            ThrowUtil.throwIf(!ChunkStrategy.TYPE.equals(row.getType()),
                    ErrorCode.STRATEGY_VERSION_NOT_FOUND, "策略类型不匹配：期望 " + ChunkStrategy.TYPE);
            ThrowUtil.throwIf(!RowStatus.ACTIVE.name().equals(row.getStatus()),
                    ErrorCode.STRATEGY_VERSION_NOT_FOUND, "策略已停用，请先启用后再触发");
            return toStrategy(row);
        }
        KbPipelineStrategyVersion latest = strategyVersionDbService.getLatestEnabledByType(ChunkStrategy.TYPE);
        return ObjectUtil.isNull(latest) ? strategyParser.defaultStrategy() : toStrategy(latest);
    }

    /** 上游预处理产物校验：指定 id 则校验存在/环节/归属；缺省取该文件结果最新 PREPROCESS 产物。 */
    private KbPipelineProduct requirePreprocessProduct(Long fileResultId, Long productId) {
        if (ObjectUtil.isNull(productId)) {
            return pipelineProductDbService.getByFileResultIdAndStage(fileResultId, PipelineStage.PREPROCESS.name());
        }
        KbPipelineProduct product = pipelineProductDbService.getById(productId);
        ThrowUtil.throwIf(ObjectUtil.isNull(product), ErrorCode.FILE_RESULT_NOT_FOUND, "指定上游产物不存在");
        ThrowUtil.throwIf(!PipelineStage.PREPROCESS.name().equals(product.getStage()), ErrorCode.FILE_RESULT_NOT_FOUND,
                "指定产物环节不匹配：期望 PREPROCESS");
        ThrowUtil.throwIf(!fileResultId.equals(product.getFileResultId()), ErrorCode.FILE_RESULT_NOT_FOUND,
                "指定产物不属于该文件结果");
        return product;
    }

    private ChunkStrategy toStrategy(KbPipelineStrategyVersion row) {
        // configSnapshot 为 routes/pipeline 结构（无 name/version），解析补全默认后回填行信息
        ChunkStrategy strategy = strategyParser.parse(row.getConfigSnapshot());
        strategy.setType(row.getType());
        strategy.setName(row.getName());
        strategy.setVersion(row.getVersion());
        return strategy;
    }
}
