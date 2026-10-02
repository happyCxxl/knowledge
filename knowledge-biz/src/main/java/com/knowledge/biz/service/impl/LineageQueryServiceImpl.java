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
import com.knowledge.common.domain.parse.CapabilitySnapshot;
import com.knowledge.common.domain.parse.ParseElement;
import com.knowledge.common.domain.parse.ParseResult;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.domain.parse.QualityInfo;
import com.knowledge.common.dto.response.lineage.LineageCapabilityVO;
import com.knowledge.common.dto.response.lineage.LineageEdgeVO;
import com.knowledge.common.dto.response.lineage.LineageNodeVO;
import com.knowledge.common.dto.response.lineage.LineageParseStatsVO;
import com.knowledge.common.dto.response.lineage.LineageVO;
import com.knowledge.common.enums.parse.ElementType;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.ThrowUtil;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.filecenter.service.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private final KbFileResultDbService fileResultDbService;
    private final KbPipelineTaskDbService pipelineTaskDbService;
    private final KbPipelineProductDbService pipelineProductDbService;
    private final KbPipelineStepLogDbService stepLogDbService;
    private final KbChunkSetDbService chunkSetDbService;
    private final KbEmbeddingSetDbService embeddingSetDbService;
    private final FileStorage fileStorage;

    @Override
    public LineageVO lineage(Long fileResultId) {
        ThrowUtil.throwIf(ObjectUtil.isNull(fileResultDbService.getById(fileResultId)),
                ErrorCode.FILE_RESULT_NOT_FOUND);

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
        Map<Long, ParseResult> parseResultByTaskId = readParseResults(tasks, productById);

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
            nodes.add(toNode(task, product, chunkSetByArtifact, embedSetByArtifact, stepAgg, parseResultByTaskId));
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
                                 Map<Long, ParseResult> parseResultByTaskId) {
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
                node.setCapability(resolveCapability(product.getCapabilitySnapshot()));
            }
            if (PipelineStage.PARSE.name().equals(task.getStage())) {
                fillParseStats(node, task, parseResultByTaskId.get(task.getId()));
            }
            fillStats(stats, task, product, chunkSetByArtifact, embedSetByArtifact, stepAgg);
        }
        node.setStats(stats);
        return node;
    }

    /**
     * 批量读取解析环节产物本体：每个解析任务至多一次对象读取，读取失败记日志并按"无统计"处理。
     *
     * @param tasks      本次血缘的任务列表
     * @param productById 产物 ID → 产物行
     * @return 解析任务 ID → 产物本体（无产物或读取失败的任务不出现在结果里）
     */
    private Map<Long, ParseResult> readParseResults(List<KbPipelineTask> tasks,
                                                    Map<Long, KbPipelineProduct> productById) {
        Map<Long, ParseResult> result = new HashMap<>();
        for (KbPipelineTask task : tasks) {
            if (!PipelineStage.PARSE.name().equals(task.getStage()) || task.getProductId() == null) {
                continue;
            }
            KbPipelineProduct product = productById.get(task.getProductId());
            if (product == null || StrUtil.isBlank(product.getArtifactId())) {
                continue;
            }
            try {
                byte[] content = fileStorage.getObject(product.getArtifactId());
                ParseResult parseResult = ObjectUtil.isNull(content) ? null
                        : JsonUtil.toObject(new String(content, StandardCharsets.UTF_8), ParseResult.class);
                if (ObjectUtil.isNotNull(parseResult)) {
                    result.put(task.getId(), parseResult);
                }
            } catch (Exception e) {
                log.warn("解析产物读取失败, taskId={}, artifactId={}", task.getId(), product.getArtifactId(), e);
            }
        }
        return result;
    }

    /**
     * 解析产物本体 → 节点统计：元素构成按顶层元素类型归类，问题单元取清单始末，
     * 页数取解析器回填的单元数（缺失回落到文件引用的页数）。
     *
     * @param task        解析任务（提供耗时）
     * @param parseResult 该次运行的产物本体，可空
     * @return 统计；产物为空时返回 null
     */
    private LineageParseStatsVO parseStats(KbPipelineTask task, ParseResult parseResult) {
        if (ObjectUtil.isNull(parseResult)) {
            return null;
        }
        List<ParseSource> sources = ObjectUtil.defaultIfNull(parseResult.getSources(), List.<ParseSource>of());
        Map<String, Integer> typeCount = new HashMap<>();
        int unitCount = 0;
        for (ParseSource source : sources) {
            for (ParseElement element : ObjectUtil.defaultIfNull(source.getElements(), List.<ParseElement>of())) {
                typeCount.merge(StrUtil.blankToDefault(element.getType(), ""), 1, Integer::sum);
            }
            unitCount = Math.max(unitCount, ObjectUtil.defaultIfNull(source.getUnitCount(), 0));
        }
        int body = typeCount.getOrDefault(ElementType.PARAGRAPH.name(), 0)
                + typeCount.getOrDefault(ElementType.LIST.name(), 0)
                + typeCount.getOrDefault(ElementType.FIGURE_CAPTION.name(), 0);
        int headerFooter = typeCount.getOrDefault(ElementType.HEADER.name(), 0)
                + typeCount.getOrDefault(ElementType.FOOTER.name(), 0);

        LineageParseStatsVO stats = new LineageParseStatsVO();
        stats.setPageCount(resolvePageCount(unitCount, parseResult));
        stats.setElementCount(typeCount.values().stream().mapToInt(Integer::intValue).sum());
        stats.setBodyCount(body);
        stats.setTableCount(typeCount.getOrDefault(ElementType.TABLE.name(), 0));
        stats.setImageCount(typeCount.getOrDefault(ElementType.IMAGE.name(), 0));
        stats.setHeaderFooterCount(headerFooter);

        List<Integer> failedUnits = failedUnits(parseResult.getQuality());
        stats.setFailedUnitCount(failedUnits.isEmpty() ? null : failedUnits.size());
        stats.setFailedFrom(failedUnits.isEmpty() ? null : failedUnits.get(0));
        stats.setFailedTo(failedUnits.isEmpty() ? null : failedUnits.get(failedUnits.size() - 1));
        stats.setDurationMs(durationMs(task));
        return stats;
    }

    /** 解析节点统计与摘要行一并回填：指标行与构成图取统计字段，摘要行按统计与状态陈述 */
    private void fillParseStats(LineageNodeVO node, KbPipelineTask task, ParseResult parseResult) {
        LineageParseStatsVO stats = parseStats(task, parseResult);
        node.setParseStats(stats);
        node.setParseSummary(parseSummary(node.getErrorMsg(), stats, task.getStatus()));
    }

    /**
     * 摘要行文案：失败取失败原因；成功与部分成功按统计里的问题单元陈述；其余状态为空。
     *
     * @param errorMsg 失败原因
     * @param stats    解析统计（**可空**：产物不可读时为 null）
     * @param status   任务状态
     * @return 摘要文案；统计缺失或无可陈述内容时返回 null
     */
    private String parseSummary(String errorMsg, LineageParseStatsVO stats, String status) {
        if (PipelineTaskStatus.FAILED.name().equals(status)
                || PipelineTaskStatus.CANCELLED.name().equals(status)) {
            return StrUtil.blankToDefault(errorMsg, "解析失败");
        }
        if (!PipelineTaskStatus.SUCCESS.name().equals(status)
                && !PipelineTaskStatus.PARTIAL_SUCCESS.name().equals(status)) {
            return null;
        }
        // 统计缺失（产物不可读）时不陈述结论：无异常的断言只在统计到手时成立
        if (ObjectUtil.isNull(stats)) {
            return null;
        }
        Integer failedUnitCount = stats.getFailedUnitCount();
        if (ObjectUtil.isNull(failedUnitCount) || failedUnitCount == 0) {
            return "无异常";
        }
        return failedUnitCount + " 单元未解析出内容（第 " + stats.getFailedFrom()
                + "–" + stats.getFailedTo() + "）";
    }

    /** 页数：优先解析器回填的判定单元数，缺失时取文件引用里的页数 */
    private Integer resolvePageCount(int unitCount, ParseResult parseResult) {
        if (unitCount > 0) {
            return unitCount;
        }
        if (ObjectUtil.isNull(parseResult.getFile())) {
            return null;
        }
        Integer pageCount = parseResult.getFile().getPageCount();
        return ObjectUtil.defaultIfNull(pageCount, 0) > 0 ? pageCount : null;
    }

    /** 问题单元清单：去重升序，供始末单元号取值 */
    private List<Integer> failedUnits(QualityInfo quality) {
        if (ObjectUtil.isNull(quality)) {
            return List.of();
        }
        return ObjectUtil.defaultIfNull(quality.getFailedPages(), List.<Integer>of()).stream()
                .filter(ObjectUtil::isNotNull)
                .distinct()
                .sorted()
                .toList();
    }

    /** 本次运行耗时：起止时间齐全时相减，缺失返回 null */
    private Long durationMs(KbPipelineTask task) {
        if (ObjectUtil.isNull(task.getStartedAt()) || ObjectUtil.isNull(task.getFinishedAt())) {
            return null;
        }
        return Duration.between(task.getStartedAt(), task.getFinishedAt()).toMillis();
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
