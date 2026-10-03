package com.knowledge.biz.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.biz.service.LineageQueryService;
import com.knowledge.biz.service.db.KbChunkSetDbService;
import com.knowledge.biz.service.db.KbEmbeddingSetDbService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineStepLogDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.biz.service.support.FileResultAccessGuard;
import com.knowledge.biz.service.support.ParseStatsSupport;
import com.knowledge.biz.service.support.PreprocessStatsSupport;
import com.knowledge.biz.service.support.StructureStatsSupport;
import com.knowledge.common.domain.entity.KbChunkSet;
import com.knowledge.common.domain.entity.KbEmbeddingSet;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineStepLog;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.parse.CapabilitySnapshot;
import com.knowledge.common.domain.parse.ParseResult;
import com.knowledge.common.domain.preprocess.PreprocessView;
import com.knowledge.common.domain.structure.UnifiedDocument;
import com.knowledge.common.dto.response.lineage.LineageCapabilityVO;
import com.knowledge.common.dto.response.lineage.LineageEdgeVO;
import com.knowledge.common.dto.response.lineage.LineageNodeVO;
import com.knowledge.common.dto.response.lineage.LineageParseStatsVO;
import com.knowledge.common.dto.response.lineage.LineageVO;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.filecenter.service.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 执行树聚合实现：批量 in 查询拼图（≈5 次 DB 访问），无 N+1。
 * 节点=一次运行；边=upstreamProductId 血缘反查（task.productId → product.id → 产出任务）；
 * 统计摘要按 artifactId 匹配（CHUNK=chunkCount、EMBED=recordCount/cachedCount、PREPROCESS=step_log 聚合），
 * PARSE 另读产物本体汇总元素构成、问题单元与耗时（每个解析任务一次对象读取）。
 *
 * @author cxxl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LineageQueryServiceImpl implements LineageQueryService {

    /** 有策略环节（strategySnapshot 为策略快照，节点展示 strategyVersion） */
    private static final Set<String> STRATEGY_STAGES = Set.of(
            PipelineStage.PREPROCESS.name(), PipelineStage.CHUNK.name(), PipelineStage.EMBED.name());

    private final KbPipelineTaskDbService pipelineTaskDbService;
    private final KbPipelineProductDbService pipelineProductDbService;
    private final KbPipelineStepLogDbService stepLogDbService;
    private final KbChunkSetDbService chunkSetDbService;
    private final KbEmbeddingSetDbService embeddingSetDbService;
    private final FileStorage fileStorage;
    private final FileResultAccessGuard accessGuard;

    @Override
    public LineageVO lineage(Long fileResultId) {
        accessGuard.requireExisting(fileResultId);

        List<KbPipelineTask> tasks = pipelineTaskDbService.listByFileResultId(fileResultId).stream()
                .filter(task -> PipelineStage.FILE_CHAIN_STAGES.contains(task.getStage()))
                .toList();
        List<KbPipelineProduct> products = pipelineProductDbService.listByFileResultId(fileResultId);

        Map<Long, KbPipelineProduct> productById = products.stream()
                .collect(Collectors.toMap(KbPipelineProduct::getId, p -> p, (a, b) -> a));
        // 产物 ID → 产出任务 ID（血缘反查；task.productId 唯一）
        Map<Long, Long> taskIdByProductId = new HashMap<>();
        for (KbPipelineTask task : tasks) {
            if (task.getProductId() != null) {
                taskIdByProductId.put(task.getProductId(), task.getId());
            }
        }

        Map<String, KbChunkSet> chunkSetByArtifact = chunkSetDbService.listByFileResultId(fileResultId).stream()
                .collect(Collectors.toMap(KbChunkSet::getArtifactId, s -> s, (a, b) -> a));
        Map<String, KbEmbeddingSet> embedSetByArtifact = embeddingSetDbService.listByFileResultId(fileResultId)
                .stream()
                .collect(Collectors.toMap(KbEmbeddingSet::getArtifactId, s -> s, (a, b) -> a));
        Map<Long, long[]> stepAgg = aggregateStepLogs(tasks);
        Map<Long, ParseResult> parseResultByTaskId = readProductBodies(tasks, productById, PipelineStage.PARSE, "解析",
                artifactId -> ParseStatsSupport.readArtifact(fileStorage, artifactId));
        Map<Long, UnifiedDocument> documentByTaskId = readProductBodies(tasks, productById, PipelineStage.STRUCTURE,
                "组装", artifactId -> StructureStatsSupport.readDocument(fileStorage, artifactId));
        Map<Long, PreprocessView> viewByTaskId = readProductBodies(tasks, productById, PipelineStage.PREPROCESS,
                "预处理", artifactId -> PreprocessStatsSupport.readView(fileStorage, artifactId));

        // 节点：环节顺序（PARSE→…→EMBED）再按任务 id 升序
        List<KbPipelineTask> ordered = tasks.stream()
                .sorted((a, b) -> {
                    int stageCompare = Integer.compare(stageOrder(a.getStage()), stageOrder(b.getStage()));
                    return stageCompare != 0 ? stageCompare : Long.compare(a.getId(), b.getId());
                })
                .toList();

        List<LineageNodeVO> nodes = new ArrayList<>();
        List<LineageEdgeVO> edges = new ArrayList<>();
        for (KbPipelineTask task : ordered) {
            KbPipelineProduct product = task.getProductId() == null ? null : productById.get(task.getProductId());
            nodes.add(toNode(task, product, chunkSetByArtifact, embedSetByArtifact, stepAgg,
                    parseResultByTaskId, documentByTaskId, viewByTaskId));
            if (task.getUpstreamProductId() != null) {
                Long fromTaskId = taskIdByProductId.get(task.getUpstreamProductId());
                if (fromTaskId != null) {
                    LineageEdgeVO edge = new LineageEdgeVO();
                    edge.setFromTaskId(fromTaskId);
                    edge.setToTaskId(task.getId());
                    edges.add(edge);
                }
            }
        }

        LineageVO vo = new LineageVO();
        vo.setFileResultId(fileResultId);
        vo.setNodes(nodes);
        vo.setEdges(edges);
        return vo;
    }

    private LineageNodeVO toNode(KbPipelineTask task, KbPipelineProduct product,
                                 Map<String, KbChunkSet> chunkSetByArtifact,
                                 Map<String, KbEmbeddingSet> embedSetByArtifact,
                                 Map<Long, long[]> stepAgg,
                                 Map<Long, ParseResult> parseResultByTaskId,
                                 Map<Long, UnifiedDocument> documentByTaskId,
                                 Map<Long, PreprocessView> viewByTaskId) {
        LineageNodeVO node = new LineageNodeVO();
        node.setTaskId(task.getId());
        node.setProductId(task.getProductId());
        node.setStage(task.getStage());
        node.setStatus(task.getStatus());
        node.setErrorCode(task.getErrorCode());
        node.setErrorMsg(task.getErrorMsg());
        node.setStartedAt(task.getStartedAt());
        node.setFinishedAt(task.getFinishedAt());
        if (STRATEGY_STAGES.contains(task.getStage())) {
            node.setStrategyVersion(resolveStrategyVersion(task.getStrategySnapshot()));
        }
        Map<String, Object> stats = new LinkedHashMap<>();
        boolean structureStage = PipelineStage.STRUCTURE.name().equals(task.getStage());
        boolean preprocessStage = PipelineStage.PREPROCESS.name().equals(task.getStage());
        if (product != null) {
            node.setArtifactId(product.getArtifactId());
            node.setContentHash(product.getContentHash());
            if (PipelineStage.PARSE.name().equals(task.getStage())
                    || PipelineStage.STRUCTURE.name().equals(task.getStage())) {
                node.setCapability(resolveCapability(product.getCapabilitySnapshot()));
            }
            if (PipelineStage.PARSE.name().equals(task.getStage())) {
                fillParseStats(node, task, parseResultByTaskId.get(task.getId()));
            }
            if (PipelineStage.STRUCTURE.name().equals(task.getStage())) {
                structureStage = true;
            }
            fillStats(stats, task, product, chunkSetByArtifact, embedSetByArtifact, stepAgg);
        }
        node.setStats(stats);
        // 组装统计在通用统计之后落位：通用 map 只覆盖切片与向量化等环节，而组装统计由本环节独占，
        // 放在它之前会被随后的空 map 覆盖掉。
        if (structureStage) {
            fillStructureStats(node, task, documentByTaskId.get(task.getId()));
        }
        // 预处理统计在通用统计之后并入：通用 map 里的 matched/changed 由 step_log 聚合而来，必须保留
        if (preprocessStage) {
            fillPreprocessStats(node, task, viewByTaskId.get(task.getId()));
        }
        return node;
    }

    /**
     * 批量读取某环节的产物本体：每个任务至多一次对象读取，读取失败记日志并按"无统计"处理。
     *
     * @param tasks       本次血缘的任务列表
     * @param productById 产物 ID → 产物行
     * @param stage       目标环节（只处理该环节的任务）
     * @param label       日志里的环节名
     * @param reader      产物引用 → 产物本体（读不到返回 null）
     * @return 任务 ID → 产物本体（无产物或读取失败的任务不出现在结果里）
     */
    private <T> Map<Long, T> readProductBodies(List<KbPipelineTask> tasks,
                                               Map<Long, KbPipelineProduct> productById, PipelineStage stage,
                                               String label, Function<String, T> reader) {
        Map<Long, T> result = new HashMap<>();
        for (KbPipelineTask task : tasks) {
            if (!stage.name().equals(task.getStage()) || task.getProductId() == null) {
                continue;
            }
            KbPipelineProduct product = productById.get(task.getProductId());
            if (product == null || StrUtil.isBlank(product.getArtifactId())) {
                continue;
            }
            T body = reader.apply(product.getArtifactId());
            if (ObjectUtil.isNotNull(body)) {
                result.put(task.getId(), body);
            } else {
                log.warn("{}产物读取失败, taskId={}, artifactId={}", label, task.getId(), product.getArtifactId());
            }
        }
        return result;
    }

    /** 解析节点统计与摘要行一并回填：指标行与构成图取统计字段，摘要行按统计与状态陈述 */
    private void fillParseStats(LineageNodeVO node, KbPipelineTask task, ParseResult parseResult) {
        LineageParseStatsVO stats = ParseStatsSupport.stats(task.getStartedAt(), task.getFinishedAt(), parseResult);
        node.setParseStats(stats);
        node.setParseSummary(ParseStatsSupport.summary(node.getErrorMsg(), stats, task.getStatus()));
    }

    /** 组装节点统计与摘要行一并回填：统计进通用 stats，摘要走通用 stageSummary */
    private void fillStructureStats(LineageNodeVO node, KbPipelineTask task, UnifiedDocument document) {
        Map<String, Object> stats =
                StructureStatsSupport.stats(task.getStartedAt(), task.getFinishedAt(), document);
        node.setStageSummary(StructureStatsSupport.summary(node.getErrorMsg(), stats, task.getStatus()));
        if (ObjectUtil.isNotNull(stats)) {
            node.setStats(stats);
        }
    }

    /**
     * 预处理节点统计与摘要行一并回填：统计并入通用 stats（保留 step_log 聚合来的 matched/changed），
     * 摘要走通用 stageSummary。
     */
    private void fillPreprocessStats(LineageNodeVO node, KbPipelineTask task, PreprocessView view) {
        Map<String, Object> stats =
                PreprocessStatsSupport.stats(task.getStartedAt(), task.getFinishedAt(), view);
        node.setStageSummary(PreprocessStatsSupport.summary(node.getErrorMsg(), stats, task.getStatus()));
        if (ObjectUtil.isNull(stats)) {
            return;
        }
        Map<String, Object> merged = ObjectUtil.isNull(node.getStats())
                ? new LinkedHashMap<>() : new LinkedHashMap<>(node.getStats());
        merged.putAll(stats);
        node.setStats(merged);
    }

    /** 统计摘要：CHUNK/EMBED 按 artifactId 匹配集合表；PREPROCESS 用 step_log 聚合 */
    private void fillStats(Map<String, Object> stats, KbPipelineTask task, KbPipelineProduct product,
                           Map<String, KbChunkSet> chunkSetByArtifact,
                           Map<String, KbEmbeddingSet> embedSetByArtifact,
                           Map<Long, long[]> stepAgg) {
        if (PipelineStage.CHUNK.name().equals(task.getStage())) {
            KbChunkSet chunkSet = chunkSetByArtifact.get(product.getArtifactId());
            if (chunkSet != null) {
                stats.put("chunkCount", chunkSet.getChunkCount());
            }
        } else if (PipelineStage.EMBED.name().equals(task.getStage())) {
            KbEmbeddingSet embedSet = embedSetByArtifact.get(product.getArtifactId());
            if (embedSet != null) {
                stats.put("recordCount", embedSet.getRecordCount());
                stats.put("cachedCount", embedSet.getCachedCount());
            }
        } else if (PipelineStage.PREPROCESS.name().equals(task.getStage())) {
            long[] agg = stepAgg.getOrDefault(task.getId(), new long[] { 0, 0 });
            stats.put("matched", agg[0]);
            stats.put("changed", agg[1]);
        }
    }

    /** 预处理统计聚合（step_log）：[0]=matchedCount 合计、[1]=changedCount 合计 */
    private Map<Long, long[]> aggregateStepLogs(List<KbPipelineTask> tasks) {
        List<Long> preprocessTaskIds = tasks.stream()
                .filter(task -> PipelineStage.PREPROCESS.name().equals(task.getStage()))
                .map(KbPipelineTask::getId)
                .toList();
        if (preprocessTaskIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, long[]> agg = new HashMap<>();
        for (KbPipelineStepLog stepLog : stepLogDbService.listByTaskIds(preprocessTaskIds)) {
            long[] sum = agg.computeIfAbsent(stepLog.getTaskId(), k -> new long[] { 0, 0 });
            sum[0] += ObjectUtil.defaultIfNull(stepLog.getMatchedCount(), 0);
            sum[1] += ObjectUtil.defaultIfNull(stepLog.getChangedCount(), 0);
        }
        return agg;
    }

    /** 策略快照 → name-version 串；解析失败或字段缺失返回 null */
    private String resolveStrategyVersion(String snapshot) {
        if (StrUtil.isBlank(snapshot)) {
            return null;
        }
        try {
            Map<String, Object> map = JsonUtil.toMap(snapshot);
            String name = String.valueOf(map.getOrDefault("name", ""));
            String version = String.valueOf(map.getOrDefault("version", ""));
            if (StrUtil.isBlank(name) || StrUtil.isBlank(version)) {
                return null;
            }
            return name + "-" + version;
        } catch (Exception e) {
            log.warn("策略快照解析失败, snapshot={}", StrUtil.maxLength(snapshot, 200));
            return null;
        }
    }

    /**
     * 能力快照 JSON 文本 → 结构化对象。
     *
     * <p>**解析成对象，不透传原文**：product.capabilitySnapshot 存的是
     * {@link CapabilitySnapshot} 序列化后的 JSON；解析在服务层完成，下发的就是可用字段
     * （与 {@link #resolveStrategyVersion} 同一口径）。
     *
     * <p>解析失败返回 null（**不回落原文**）：脏数据不继续往上层传。
     *
     * @param snapshot 产物上的能力快照 JSON 文本，可空
     * @return 结构化能力快照；快照为空或解析失败返回 null
     */
    private LineageCapabilityVO resolveCapability(String snapshot) {
        if (StrUtil.isBlank(snapshot)) {
            return null;
        }
        try {
            CapabilitySnapshot src = JsonUtil.toObject(snapshot, CapabilitySnapshot.class);
            if (src == null) {
                return null;
            }
            LineageCapabilityVO vo = new LineageCapabilityVO();
            vo.setParserName(src.getParserName());
            vo.setParserVersion(src.getParserVersion());
            vo.setOcr(toCapabilityRef(src.getOcr()));
            vo.setLayout(toCapabilityRef(src.getLayout()));
            vo.setTable(toCapabilityRef(src.getTable()));
            return vo;
        } catch (Exception e) {
            log.warn("能力快照解析失败, snapshot={}", StrUtil.maxLength(snapshot, 200));
            return null;
        }
    }

    /** 领域层能力引用 → 接口层能力引用；入参为空则返回空 */
    private LineageCapabilityVO.CapabilityRefVO toCapabilityRef(CapabilitySnapshot.CapabilityRef src) {
        if (src == null) {
            return null;
        }
        LineageCapabilityVO.CapabilityRefVO vo = new LineageCapabilityVO.CapabilityRefVO();
        vo.setModel(src.getModel());
        vo.setVersion(src.getVersion());
        return vo;
    }

    private int stageOrder(String stage) {
        return switch (stage) {
            case "PARSE" -> 0;
            case "STRUCTURE" -> 1;
            case "PREPROCESS" -> 2;
            case "CHUNK" -> 3;
            case "EMBED" -> 4;
            default -> 5;
        };
    }
}
