package com.knowledge.biz.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.biz.service.LineageQueryService;
import com.knowledge.biz.service.db.KbChunkSetDbService;
import com.knowledge.biz.service.db.KbEmbeddingSetDbService;
import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineStepLogDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.common.domain.entity.KbChunkSet;
import com.knowledge.common.domain.entity.KbEmbeddingSet;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineStepLog;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.dto.response.lineage.LineageEdgeVO;
import com.knowledge.common.dto.response.lineage.LineageNodeVO;
import com.knowledge.common.dto.response.lineage.LineageVO;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.ThrowUtil;
import com.knowledge.common.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 执行树聚合实现（step-12 B1）：批量 in 查询拼图（≈5 次 DB 访问），无 N+1。
 * 节点=一次运行；边=upstreamProductId 血缘反查（task.productId → product.id → 产出任务）；
 * 统计摘要按 artifactId 匹配（CHUNK=chunkCount、EMBED=recordCount/cachedCount、PREPROCESS=step_log 聚合）。
 *
 * @author cxxl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LineageQueryServiceImpl implements LineageQueryService {

    /** 执行树覆盖的环节（不含 BUILD_INDEX/RETRIEVAL_TEST） */
    private static final Set<String> LINEAGE_STAGES = Set.of(
            PipelineStage.PARSE.name(), PipelineStage.STRUCTURE.name(), PipelineStage.PREPROCESS.name(),
            PipelineStage.CHUNK.name(), PipelineStage.EMBED.name());

    /** 有策略环节（strategySnapshot 为策略快照，节点展示 strategyVersion） */
    private static final Set<String> STRATEGY_STAGES = Set.of(
            PipelineStage.PREPROCESS.name(), PipelineStage.CHUNK.name(), PipelineStage.EMBED.name());

    private final KbFileResultDbService fileResultDbService;
    private final KbPipelineTaskDbService pipelineTaskDbService;
    private final KbPipelineProductDbService pipelineProductDbService;
    private final KbPipelineStepLogDbService stepLogDbService;
    private final KbChunkSetDbService chunkSetDbService;
    private final KbEmbeddingSetDbService embeddingSetDbService;

    @Override
    public LineageVO lineage(Long fileResultId) {
        ThrowUtil.throwIf(ObjectUtil.isNull(fileResultDbService.getById(fileResultId)),
                ErrorCode.FILE_RESULT_NOT_FOUND);

        List<KbPipelineTask> tasks = pipelineTaskDbService.listByFileResultId(fileResultId).stream()
                .filter(task -> LINEAGE_STAGES.contains(task.getStage()))
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
            nodes.add(toNode(task, product, chunkSetByArtifact, embedSetByArtifact, stepAgg));
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
                                 Map<Long, long[]> stepAgg) {
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
        Map<String, String> stats = new LinkedHashMap<>();
        if (product != null) {
            node.setArtifactId(product.getArtifactId());
            node.setContentHash(product.getContentHash());
            if (PipelineStage.PARSE.name().equals(task.getStage())
                    || PipelineStage.STRUCTURE.name().equals(task.getStage())) {
                node.setCapability(product.getCapabilitySnapshot());
            }
            fillStats(stats, task, product, chunkSetByArtifact, embedSetByArtifact, stepAgg);
        }
        node.setStats(stats);
        return node;
    }

    /** 统计摘要：CHUNK/EMBED 按 artifactId 匹配集合表；PREPROCESS 用 step_log 聚合 */
    private void fillStats(Map<String, String> stats, KbPipelineTask task, KbPipelineProduct product,
                           Map<String, KbChunkSet> chunkSetByArtifact,
                           Map<String, KbEmbeddingSet> embedSetByArtifact,
                           Map<Long, long[]> stepAgg) {
        if (PipelineStage.CHUNK.name().equals(task.getStage())) {
            KbChunkSet chunkSet = chunkSetByArtifact.get(product.getArtifactId());
            if (chunkSet != null) {
                stats.put("chunkCount", String.valueOf(chunkSet.getChunkCount()));
            }
        } else if (PipelineStage.EMBED.name().equals(task.getStage())) {
            KbEmbeddingSet embedSet = embedSetByArtifact.get(product.getArtifactId());
            if (embedSet != null) {
                stats.put("recordCount", String.valueOf(embedSet.getRecordCount()));
                stats.put("cachedCount", String.valueOf(embedSet.getCachedCount()));
            }
        } else if (PipelineStage.PREPROCESS.name().equals(task.getStage())) {
            long[] agg = stepAgg.getOrDefault(task.getId(), new long[] { 0, 0 });
            stats.put("matched", String.valueOf(agg[0]));
            stats.put("changed", String.valueOf(agg[1]));
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
