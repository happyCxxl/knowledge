package com.knowledge.biz.service.impl;

import cn.hutool.core.util.StrUtil;
import com.knowledge.biz.service.ChunkControlService;
import com.knowledge.biz.service.db.*;
import com.knowledge.biz.service.support.ChunkStatsSupport;
import com.knowledge.biz.service.support.ChunkVoAssembler;
import com.knowledge.biz.service.support.PreprocessStatsSupport;
import com.knowledge.biz.service.support.StageStrategySupport;
import com.knowledge.biz.service.support.FileResultAccessGuard;
import com.knowledge.biz.service.support.TaskDetailSupport;
import com.knowledge.biz.task.TaskTriggerSupport;
import com.knowledge.common.domain.chunk.Chunk;
import com.knowledge.common.domain.chunk.ChunkSet;
import com.knowledge.common.domain.entity.*;
import com.knowledge.common.domain.preprocess.PreprocessView;
import com.knowledge.common.domain.rules.ChunkRules;
import com.knowledge.common.domain.storage.ObjectRef;
import com.knowledge.common.dto.response.chunk.ChunkDetailVO;
import com.knowledge.common.dto.response.chunk.ChunkTriggerVO;
import com.knowledge.common.dto.response.task.StageTriggerVO;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.ThrowUtil;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.common.utils.NullUtil;
import com.knowledge.filecenter.service.FileStorage;
import com.knowledge.worker.chunking.strategy.ChunkStrategy;
import com.knowledge.worker.chunking.strategy.ChunkStrategyParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    private final KbPipelineProductDbService pipelineProductDbService;
    private final StageStrategySupport strategySupport;
    private final TaskTriggerSupport triggerSupport;
    private final TaskDetailSupport detailSupport;
    private final KbChunkSetDbService chunkSetDbService;
    private final KbChunkDbService chunkDbService;
    private final ChunkStrategyParser strategyParser;
    private final ChunkVoAssembler voAssembler;
    private final FileStorage fileStorage;
    private final FileResultAccessGuard accessGuard;

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
        KbFileResult fileResult = accessGuard.requireExisting(fileResultId);
        ChunkStrategy strategy = resolveStrategy(fileResult, strategyVersionId);

        // 可选指定上游预处理产物；缺省取最新
        KbPipelineProduct preprocessProduct = requirePreprocessProduct(fileResultId, upstreamProductId);
        ThrowUtil.throwIf(NullUtil.isNull(preprocessProduct), ErrorCode.FILE_RESULT_NOT_FOUND,
                ChunkRules.UPSTREAM_PREPROCESS_MISSING);

        StageTriggerVO triggered = triggerSupport.trigger(fileResultId, PipelineStage.CHUNK,
                preprocessProduct.getId(), JsonUtil.toJsonStr(strategy), "切片", false);
        return new ChunkTriggerVO(fileResultId, triggered.getPipelineTaskId(), strategy.fullVersion());
    }

    @Override
    public ChunkDetailVO chunkDetail(Long fileResultId, Long taskId) {
        accessGuard.requireExisting(fileResultId);
        KbPipelineTask task = detailSupport.resolveTask(fileResultId, PipelineStage.CHUNK, taskId, "切片");

        ChunkDetailVO vo = new ChunkDetailVO();
        detailSupport.withTask(vo, fileResultId, task);

        // 切片集合/切片列表：按 task.productId → 产物 → artifactId 精确取该次运行的集合（历史任务同样可展示自己的集合；无任务/无产物留空）
        vo.setChunks(new ArrayList<>());
        ChunkSet chunkSet = null;
        KbPipelineProduct product = detailSupport.productOfTask(task);
        if (NullUtil.isNotNull(product)) {
            // 产物侧现读一次：统计与来源元素个数 / 兜底原因都只在这份产物里（kb_chunk 无对应列）
            chunkSet = ChunkStatsSupport.readChunkSet(fileStorage, ObjectRef.ofProduct(product));
            KbChunkSet chunkSetRow = chunkSetDbService.getByArtifactId(product.getArtifactId());
            if (NullUtil.isNotNull(chunkSetRow)) {
                List<KbChunk> chunks = chunkDbService.listByChunkSetId(chunkSetRow.getId());
                vo.setSummary(voAssembler.toSummary(chunkSetRow, chunks));
                vo.setChunks(voAssembler.toChunkItemVOs(chunks, productChunks(chunkSet)));
            }
            detailSupport.withProductRef(vo, product);
        }
        // 统计与摘要读产物现算：与执行树切片节点同一份口径，不落产物；产物读不到时不陈述结论
        Map<String, Object> stageStats = ChunkStatsSupport.stats(vo.getStartedAt(), vo.getFinishedAt(), chunkSet,
                strategyOf(task), readUpstreamView(task));
        vo.setStageStats(stageStats);
        vo.setStageSummary(ChunkStatsSupport.summary(vo.getErrorMsg(), stageStats, vo.getStatus()));
        return vo;
    }

    /** 任务快照 → 切片策略（片长上限与兜底参数从它取；无任务 / 快照缺失回退内置默认） */
    private ChunkStrategy strategyOf(KbPipelineTask task) {
        return strategyParser.parse(NullUtil.isNull(task) ? null : task.getStrategySnapshot());
    }

    /** 产物里的片按 chunkId 建索引（产物读不到时为空表） */
    private Map<String, Chunk> productChunks(ChunkSet chunkSet) {
        if (NullUtil.isNull(chunkSet) || NullUtil.isNull(chunkSet.getChunks())) {
            return Map.of();
        }
        return chunkSet.getChunks().stream()
                .filter(chunk -> StrUtil.isNotBlank(chunk.getChunkId()))
                .collect(Collectors.toMap(Chunk::getChunkId, chunk -> chunk, (first, second) -> first));
    }

    /** 读上游预处理视图（"进入切片 / 跳过"元素数的口径来源；取不到返回 null） */
    private PreprocessView readUpstreamView(KbPipelineTask task) {
        if (NullUtil.isNull(task) || NullUtil.isNull(task.getUpstreamProductId())) {
            return null;
        }
        KbPipelineProduct upstream = pipelineProductDbService.getById(task.getUpstreamProductId());
        return NullUtil.isNull(upstream)
                ? null : PreprocessStatsSupport.readView(fileStorage, ObjectRef.ofProduct(upstream));
    }

    /** 策略解析四档：显式指定（40433 校验存在/类型/启用）→ KB 绑定（开关开启时，失效回退告警）→ 启用中最新 → 内置默认。 */
    private ChunkStrategy resolveStrategy(KbFileResult fileResult, Long strategyVersionId) {
        KbPipelineStrategyVersion row = strategySupport.resolve(
                fileResult, strategyVersionId, ChunkStrategy.TYPE, "ChunkControlServiceImpl 切片");
        return NullUtil.isNull(row) ? strategyParser.defaultStrategy() : toStrategy(row);
    }

    /** 上游预处理产物校验：指定 id 则校验存在/环节/归属；缺省取该文件结果最新 PREPROCESS 产物。 */
    private KbPipelineProduct requirePreprocessProduct(Long fileResultId, Long productId) {
        if (NullUtil.isNull(productId)) {
            return pipelineProductDbService.getByFileResultIdAndStage(fileResultId, PipelineStage.PREPROCESS.name());
        }
        KbPipelineProduct product = pipelineProductDbService.getById(productId);
        ThrowUtil.throwIf(NullUtil.isNull(product), ErrorCode.FILE_RESULT_NOT_FOUND, "指定上游产物不存在");
        ThrowUtil.throwIf(!PipelineStage.PREPROCESS.name().equals(product.getStage()), ErrorCode.FILE_RESULT_NOT_FOUND,
                "指定产物环节不匹配：期望 PREPROCESS");
        ThrowUtil.throwIf(!fileResultId.equals(product.getFileResultId()), ErrorCode.FILE_RESULT_NOT_FOUND,
                "指定产物不属于该文件结果");
        return product;
    }

    private ChunkStrategy toStrategy(KbPipelineStrategyVersion row) {
        // configSnapshot 为 routes/pipeline 结构（无 name/version），解析补全默认后回填行信息
        return strategySupport.bindMeta(strategyParser.parse(row.getConfigSnapshot()), row);
    }
}
