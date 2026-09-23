package com.knowledge.biz.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.biz.service.SearchService;
import com.knowledge.biz.service.db.KbIndexSetDbService;
import com.knowledge.biz.service.db.KbIndexVersionDbService;
import com.knowledge.biz.service.db.KbPipelineStrategyVersionDbService;
import com.knowledge.biz.service.db.KbRetrievalRunDbService;
import com.knowledge.biz.service.db.KnowledgeBaseDbService;
import com.knowledge.biz.service.support.RetrievalRuleResolver;
import com.knowledge.biz.service.support.RetrievalRunSettings;
import com.knowledge.common.domain.embed.EmbeddingRequest;
import com.knowledge.common.domain.embed.EmbeddingResult;
import com.knowledge.common.domain.entity.KbIndexSet;
import com.knowledge.common.domain.entity.KbIndexVersion;
import com.knowledge.common.domain.entity.KbPipelineStrategyVersion;
import com.knowledge.common.domain.entity.KbRetrievalRun;
import com.knowledge.common.domain.entity.KnowledgeBase;
import com.knowledge.common.dto.request.retrieval.SearchRequest;
import com.knowledge.common.dto.response.retrieval.RetrievalRunVO;
import com.knowledge.common.dto.response.retrieval.SearchHitVO;
import com.knowledge.common.dto.response.retrieval.SearchVO;
import com.knowledge.common.enums.index.IndexVersionStatus;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.ThrowUtil;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.model.gateway.ModelGatewayPort;
import com.knowledge.worker.embedding.strategy.EmbedStrategy;
import com.knowledge.worker.embedding.strategy.EmbedStrategyParser;
import com.knowledge.worker.indexing.ComboSnapshot;
import com.knowledge.worker.indexing.IndexRow;
import com.knowledge.worker.indexing.MilvusIndexPort;
import com.knowledge.worker.indexing.search.FullTextHit;
import com.knowledge.worker.indexing.search.FullTextQuery;
import com.knowledge.worker.indexing.search.VectorHit;
import com.knowledge.worker.indexing.search.VectorQuery;
import com.knowledge.worker.retrieval.RetrievalRuleSpec;
import com.knowledge.worker.retrieval.RrfFusion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 检索引擎编排实现（step-14 B4，B09）：唯一执行路径 search(kbId, query, ruleSpec, collectionName)。
 * 生产检索（回退链）与测试台检索（显式版本+规则）同引擎；预留能力经 RetrievalRuleResolver.validate
 * 双保险拒执行；查询向量化跟随集合 EMBED 策略（queryTemplate 口径）；网关失败该通道降级为空。
 *
 * @author cxxl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final KbIndexSetDbService indexSetDbService;

    private final KbIndexVersionDbService indexVersionDbService;

    private final KnowledgeBaseDbService knowledgeBaseDbService;

    private final KbPipelineStrategyVersionDbService strategyVersionDbService;

    private final MilvusIndexPort milvusIndexPort;

    private final ModelGatewayPort modelGatewayPort;

    private final EmbedStrategyParser embedStrategyParser;

    private final RetrievalRuleResolver retrievalRuleResolver;

    private final KbRetrievalRunDbService retrievalRunDbService;

    private final RetrievalRunSettings runSettings;

    @Override
    public SearchVO search(Long knowledgeBaseId, SearchRequest request) {
        long start = System.currentTimeMillis();
        boolean bench = ObjectUtil.isNotNull(request.getVersionId()) || ObjectUtil.isNotNull(request.getRuleId());
        ThrowUtil.throwIf(bench && (ObjectUtil.isNull(request.getVersionId()) || ObjectUtil.isNull(request.getRuleId())),
                ErrorCode.PARAM_INVALID, "测试台检索需同时指定 versionId 与 ruleId");

        // ① 版本 + 规则（生产=在线+回退链；测试台=显式）
        KbIndexVersion version = resolveVersion(knowledgeBaseId, request, bench);
        KbPipelineStrategyVersion ruleRow = resolveRuleRow(knowledgeBaseId, version, request.getRuleId());
        // 引擎防御：预留项拒执行（注册侧已拦，双保险防数据篡改/历史数据）
        RetrievalRuleSpec spec = retrievalRuleResolver.validate(ruleRow.getConfigSnapshot());
        String collectionName = MilvusIndexPort.collectionName(knowledgeBaseId, version.getVersionNo());

        // ② 双通道召回（FULLTEXT 跳过向量化；VECTOR 跳过全文）
        List<FullTextHit> fullTextHits = List.of();
        List<VectorHit> vectorHits = List.of();
        if (!RetrievalRuleSpec.CHANNEL_VECTOR.equals(spec.getChannel())) {
            fullTextHits = milvusIndexPort.searchFullText(collectionName, FullTextQuery.builder()
                    .keyword(request.getQuery())
                    .limit(spec.getFusion().getPerChannelLimit())
                    .documentId(request.getDocumentId())
                    .owner(request.getOwner())
                    .contentType(request.getContentType())
                    .build());
        }
        if (!RetrievalRuleSpec.CHANNEL_FULLTEXT.equals(spec.getChannel())) {
            List<Float> queryVector = embedQuery(version, request.getQuery());
            if (ObjectUtil.isNotNull(queryVector)) {
                vectorHits = milvusIndexPort.searchVector(collectionName, VectorQuery.builder()
                        .vector(queryVector)
                        .topK(spec.getFusion().getPerChannelLimit())
                        .documentId(request.getDocumentId())
                        .owner(request.getOwner())
                        .contentType(request.getContentType())
                        .build());
            }
        }

        // ③ 融合/直通（HYBRID=RRF；单通道直通）
        List<SearchHitVO> ordered = orderHits(spec, fullTextHits, vectorHits);

        // ④ 父片展开（子片命中 → 集合内取父片全文；缺失保留子片原文）
        if (RetrievalRuleSpec.MODE_PARENT_EXPAND.equals(spec.getPostprocess().getMode())) {
            ordered = expandParent(collectionName, ordered);
        }

        // ⑤ Top-K（请求覆盖规则）
        int topK = ObjectUtil.isNotNull(request.getTopK()) ? request.getTopK() : spec.getTopK();
        ordered = ordered.stream().limit(topK).toList();

        SearchVO vo = new SearchVO();
        vo.setQuery(request.getQuery());
        vo.setRuleNameVersion(ruleRow.getName() + "-" + ruleRow.getVersion());
        vo.setVersionNo(version.getVersionNo());
        vo.setElapsedMs(System.currentTimeMillis() - start);
        vo.setHits(ordered);

        // ⑥ 留痕：测试台必记；生产检索按开关（默认关）。快照即证据——对比回放不回跑。
        if (bench || runSettings.recordProduction()) {
            recordRun(knowledgeBaseId, version, ruleRow, request, vo);
        }
        return vo;
    }

    @Override
    public List<RetrievalRunVO> listRuns(Long knowledgeBaseId, int limit) {
        return retrievalRunDbService.listByKb(knowledgeBaseId, limit).stream()
                .map(this::toRunVO)
                .toList();
    }

    @Override
    public List<SearchVO> compareRuns(Long knowledgeBaseId, List<Long> runIds) {
        ThrowUtil.throwIf(runIds == null || runIds.isEmpty(), ErrorCode.PARAM_INVALID, "runIds 不能为空");
        List<KbRetrievalRun> runs = retrievalRunDbService.listByIdsOrdered(runIds);
        List<SearchVO> vos = new ArrayList<>(runs.size());
        for (KbRetrievalRun run : runs) {
            ThrowUtil.throwIf(!knowledgeBaseId.equals(run.getKbId()),
                    ErrorCode.PARAM_INVALID, "运行记录不属于该知识库: " + run.getId());
            SearchVO vo = new SearchVO();
            vo.setQuery(run.getQuery());
            vo.setRuleNameVersion(run.getRuleNameVersion());
            vo.setVersionNo(run.getVersionNo());
            vo.setElapsedMs(run.getElapsedMs() == null ? 0L : run.getElapsedMs().longValue());
            vo.setRunId(run.getId());
            vo.setHits(JsonUtil.toList(run.getResultSnapshot(), SearchHitVO.class));
            vos.add(vo);
        }
        return vos;
    }

    /** 运行记录落库（快照 = 命中列表全字段 JSON；baseline 规则行无 id → rule_id 记 NULL） */
    private void recordRun(Long knowledgeBaseId, KbIndexVersion version, KbPipelineStrategyVersion ruleRow,
                           SearchRequest request, SearchVO vo) {
        KbRetrievalRun run = new KbRetrievalRun();
        run.setKbId(knowledgeBaseId);
        run.setVersionId(version.getId());
        run.setVersionNo(version.getVersionNo());
        run.setRuleId(ruleRow.getId());
        run.setRuleNameVersion(ruleRow.getName() + "-" + ruleRow.getVersion());
        run.setQuery(request.getQuery());
        run.setResultSnapshot(JsonUtil.toJsonStr(vo.getHits()));
        run.setElapsedMs(vo.getElapsedMs() == null ? 0 : vo.getElapsedMs().intValue());
        retrievalRunDbService.save(run);
        vo.setRunId(run.getId());
    }

    private RetrievalRunVO toRunVO(KbRetrievalRun run) {
        RetrievalRunVO vo = new RetrievalRunVO();
        vo.setId(run.getId());
        vo.setVersionId(run.getVersionId());
        vo.setVersionNo(run.getVersionNo());
        vo.setRuleId(run.getRuleId());
        vo.setRuleNameVersion(run.getRuleNameVersion());
        vo.setQuery(run.getQuery());
        vo.setElapsedMs(run.getElapsedMs());
        vo.setCreateTime(run.getCreateTime());
        return vo;
    }

    /** 版本解析：测试台=显式版本行（含候选冻结集，READY/ONLINE 可检索）；生产=在线版本（未发布 40446） */
    private KbIndexVersion resolveVersion(Long knowledgeBaseId, SearchRequest request, boolean bench) {
        if (bench) {
            KbIndexVersion version = indexVersionDbService.getById(request.getVersionId());
            ThrowUtil.throwIf(ObjectUtil.isNull(version), ErrorCode.INDEX_VERSION_NOT_FOUND);
            ThrowUtil.throwIf(!Set.of(IndexVersionStatus.READY.name(), IndexVersionStatus.ONLINE.name())
                            .contains(version.getStatus()),
                    ErrorCode.INDEX_BUILDING_CONFLICT, "仅就绪/在线版本可检索");
            return version;
        }
        KbIndexSet set = indexSetDbService.getByKb(knowledgeBaseId);
        ThrowUtil.throwIf(ObjectUtil.isNull(set) || ObjectUtil.isNull(set.getCurrentPublishedVersionId()),
                ErrorCode.INDEX_NOT_PUBLISHED);
        KbIndexVersion version = indexVersionDbService.getById(set.getCurrentPublishedVersionId());
        ThrowUtil.throwIf(ObjectUtil.isNull(version), ErrorCode.INDEX_NOT_PUBLISHED);
        return version;
    }

    /** 规则解析：显式 ruleId → 版本行默认 → kb 默认 → 引擎基线（不落库常量） */
    private KbPipelineStrategyVersion resolveRuleRow(Long knowledgeBaseId, KbIndexVersion version, Long explicitRuleId) {
        Long ruleId = ObjectUtil.isNotNull(explicitRuleId) ? explicitRuleId : version.getDefaultRuleId();
        if (ObjectUtil.isNull(ruleId)) {
            KnowledgeBase kb = knowledgeBaseDbService.getById(knowledgeBaseId);
            ruleId = ObjectUtil.isNull(kb) ? null : kb.getDefaultRuleId();
        }
        if (ObjectUtil.isNotNull(ruleId)) {
            KbPipelineStrategyVersion row = strategyVersionDbService.getById(ruleId);
            ThrowUtil.throwIf(ObjectUtil.isNull(row) || !"RETRIEVAL".equals(row.getType()),
                    ErrorCode.RETRIEVAL_RULE_NOT_FOUND);
            return row;
        }
        KbPipelineStrategyVersion baseline = new KbPipelineStrategyVersion();
        baseline.setType("RETRIEVAL");
        baseline.setName("baseline");
        baseline.setVersion("v0");
        baseline.setConfigSnapshot(JsonUtil.toJsonStr(RetrievalRuleSpec.baseline()));
        return baseline;
    }

    /** 查询向量化：跟随集合 EMBED 策略（模型 + queryTemplate）；网关失败 → null（该通道降级为空） */
    private List<Float> embedQuery(KbIndexVersion version, String query) {
        ComboSnapshot combo = JsonUtil.toObject(version.getComboSnapshot(), ComboSnapshot.class);
        if (ObjectUtil.isNull(combo) || !combo.hasCompleteStageStrategies()) {
            log.warn("===> SearchServiceImpl 查询向量化中止：组合快照缺失环节策略维度, versionId={}", version.getId());
            return null;
        }
        KbPipelineStrategyVersion row = strategyVersionDbService.getByTypeAndNameAndVersion(
                "EMBED", strategyNameOf(combo.getEmbedStrategy()), strategyVersionOf(combo.getEmbedStrategy()));
        if (ObjectUtil.isNull(row)) {
            log.warn("===> SearchServiceImpl 查询向量化中止：EMBED 策略版本行不存在, strategy={}",
                    combo.getEmbedStrategy());
            return null;
        }
        EmbedStrategy strategy = embedStrategyParser.parse(row.getConfigSnapshot());
        String template = StrUtil.isBlank(strategy.getQueryTemplate()) ? "{query}" : strategy.getQueryTemplate();
        String input = template.replace("{query}", query);
        EmbeddingRequest request = new EmbeddingRequest();
        request.setModel(strategy.getModel());
        request.setTexts(List.of(input));
        request.setTimeoutMs(strategy.getTimeoutMs());
        request.setRequestId(UUID.randomUUID().toString());
        try {
            EmbeddingResult result = modelGatewayPort.embed(request);
            if (ObjectUtil.isNull(result) || ObjectUtil.isNull(result.getEmbeddings())
                    || result.getEmbeddings().isEmpty()) {
                return null;
            }
            return result.getEmbeddings().getFirst();
        } catch (Exception e) {
            log.warn("===> SearchServiceImpl 查询向量化失败，向量通道降级为空, versionId={}", version.getId(), e);
            return null;
        }
    }

    private String strategyNameOf(String nameVersion) {
        int split = nameVersion.lastIndexOf('-');
        return split > 0 ? nameVersion.substring(0, split) : nameVersion;
    }

    private String strategyVersionOf(String nameVersion) {
        int split = nameVersion.lastIndexOf('-');
        return split > 0 ? nameVersion.substring(split + 1) : "";
    }

    /** 融合/直通：HYBRID → RRF；单通道 → 原通道顺序（分数：向量=相似度，全文=null） */
    private List<SearchHitVO> orderHits(RetrievalRuleSpec spec, List<FullTextHit> fullTextHits,
                                        List<VectorHit> vectorHits) {
        if (RetrievalRuleSpec.CHANNEL_HYBRID.equals(spec.getChannel())) {
            Map<String, VectorHit> vectorByChunk = vectorHits.stream()
                    .collect(Collectors.toMap(VectorHit::getChunkId, Function.identity(), (a, b) -> a));
            Map<String, FullTextHit> fullTextByChunk = fullTextHits.stream()
                    .collect(Collectors.toMap(FullTextHit::getChunkId, Function.identity(), (a, b) -> a));
            List<RrfFusion.FusedHit> fused = RrfFusion.fuse(
                    List.of(fullTextHits.stream().map(FullTextHit::getChunkId).toList(),
                            vectorHits.stream().map(VectorHit::getChunkId).toList()),
                    spec.getFusion().getRrfK());
            List<SearchHitVO> ordered = new ArrayList<>(fused.size());
            for (RrfFusion.FusedHit fusedHit : fused) {
                VectorHit vectorHit = vectorByChunk.get(fusedHit.chunkId());
                if (ObjectUtil.isNotNull(vectorHit)) {
                    ordered.add(toVO(vectorHit, fusedHit.score()));
                } else {
                    ordered.add(toVO(fullTextByChunk.get(fusedHit.chunkId()), fusedHit.score()));
                }
            }
            return ordered;
        }
        if (RetrievalRuleSpec.CHANNEL_FULLTEXT.equals(spec.getChannel())) {
            return fullTextHits.stream().map(hit -> toVO(hit, null)).toList();
        }
        return vectorHits.stream()
                .map(hit -> toVO(hit, hit.getScore() == null ? null : hit.getScore().doubleValue()))
                .toList();
    }

    /** 父片展开：集合内批量取父片行 → 父片全文替换、isParent=true；按父片去重保留最高分；缺父片保留子片 */
    private List<SearchHitVO> expandParent(String collectionName, List<SearchHitVO> ordered) {
        List<String> parentIds = ordered.stream()
                .map(SearchHitVO::getParentChunkId)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
        if (parentIds.isEmpty()) {
            return ordered;
        }
        Map<String, IndexRow> parentRows = milvusIndexPort.queryChunkByIds(collectionName, parentIds).stream()
                .collect(Collectors.toMap(IndexRow::getChunkId, Function.identity(), (a, b) -> a));
        Map<String, SearchHitVO> deduped = new LinkedHashMap<>();
        for (SearchHitVO hit : ordered) {
            IndexRow parent = StrUtil.isBlank(hit.getParentChunkId()) ? null : parentRows.get(hit.getParentChunkId());
            String dedupeKey = ObjectUtil.isNull(parent) ? hit.getChunkId() : parent.getChunkId();
            if (deduped.containsKey(dedupeKey)) {
                continue; // 同父片多子片命中 → 保留最高融合分（已按序遍历）
            }
            if (ObjectUtil.isNotNull(parent)) {
                hit.setContent(parent.getContent());
                hit.setTitlePath(parent.getTitlePath());
                hit.setChunkId(parent.getChunkId());
                hit.setDocumentId(parent.getDocumentId());
                hit.setParentChunkId(parent.getParentChunkId());
                hit.setIsParent(true);
            }
            deduped.put(dedupeKey, hit);
        }
        return new ArrayList<>(deduped.values());
    }

    private SearchHitVO toVO(VectorHit hit, Double score) {
        return toVO(hit.getChunkId(), hit.getContent(), hit.getTitlePath(), hit.getSourceElementIds(),
                hit.getDocumentId(), hit.getContentType(), hit.getParentChunkId(), score);
    }

    private SearchHitVO toVO(FullTextHit hit, Double score) {
        return toVO(hit.getChunkId(), hit.getContent(), hit.getTitlePath(), hit.getSourceElementIds(),
                hit.getDocumentId(), hit.getContentType(), hit.getParentChunkId(), score);
    }

    /** 命中字段原样入 VO（命中直取不回查库） */
    private SearchHitVO toVO(String chunkId, String content, String titlePath, String sourceElementIds,
                             Long documentId, String contentType, String parentChunkId, Double score) {
        SearchHitVO vo = new SearchHitVO();
        vo.setChunkId(chunkId);
        vo.setContent(content);
        vo.setTitlePath(titlePath);
        vo.setSourceElementIds(sourceElementIds);
        vo.setDocumentId(documentId);
        vo.setContentType(contentType);
        vo.setParentChunkId(parentChunkId);
        vo.setScore(score);
        vo.setIsParent(false);
        return vo;
    }
}
