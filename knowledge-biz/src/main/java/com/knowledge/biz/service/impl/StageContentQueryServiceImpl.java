package com.knowledge.biz.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.biz.service.StageContentQueryService;
import com.knowledge.biz.service.db.KbChunkDbService;
import com.knowledge.biz.service.db.KbChunkSetDbService;
import com.knowledge.biz.service.db.KbEmbeddingRecordDbService;
import com.knowledge.biz.service.db.KbEmbeddingSetDbService;
import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.support.TaskDetailSupport;
import com.knowledge.common.domain.entity.KbChunk;
import com.knowledge.common.domain.entity.KbChunkSet;
import com.knowledge.common.domain.entity.KbEmbeddingRecord;
import com.knowledge.common.domain.entity.KbEmbeddingSet;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.parse.ParseElement;
import com.knowledge.common.domain.parse.ParseResult;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.domain.preprocess.PreprocessView;
import com.knowledge.common.domain.preprocess.ViewElement;
import com.knowledge.common.domain.structure.UnifiedDocument;
import com.knowledge.common.domain.structure.UnifiedElement;
import com.knowledge.common.dto.response.stagecontent.StageContentItemVO;
import com.knowledge.common.dto.response.stagecontent.StageContentVO;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.ThrowUtil;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.filecenter.service.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 产物内容查询实现（step-12 B2，恢复 T3）：
 * 白名单五环节；按 task.productId 精确取该次运行的产物内容（与详情接口同口径，历史任务同样可展示）；
 * 对齐键：PARSE/STRUCTURE/PREPROCESS=elementId、CHUNK/EMBED=顺序号（口径见 step-12 实现文档 §3.4）。
 *
 * @author cxxl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StageContentQueryServiceImpl implements StageContentQueryService {

    /** 内容接口环节白名单 */
    private static final Set<String> CONTENT_STAGES = Set.of(
            PipelineStage.PARSE.name(), PipelineStage.STRUCTURE.name(), PipelineStage.PREPROCESS.name(),
            PipelineStage.CHUNK.name(), PipelineStage.EMBED.name());

    private final KbFileResultDbService fileResultDbService;
    private final KbPipelineProductDbService pipelineProductDbService;
    private final KbChunkSetDbService chunkSetDbService;
    private final KbChunkDbService chunkDbService;
    private final KbEmbeddingSetDbService embeddingSetDbService;
    private final KbEmbeddingRecordDbService embeddingRecordDbService;
    private final FileStorage fileStorage;
    private final TaskDetailSupport detailSupport;

    @Override
    public StageContentVO stageContent(Long fileResultId, String stage, Long taskId) {
        ThrowUtil.throwIf(ObjectUtil.isNull(fileResultDbService.getById(fileResultId)),
                ErrorCode.FILE_RESULT_NOT_FOUND);
        ThrowUtil.throwIf(StrUtil.isBlank(stage) || !CONTENT_STAGES.contains(stage),
                ErrorCode.PARAM_INVALID, "未知环节: " + stage);
        KbPipelineTask task = detailSupport.resolveTask(fileResultId, stageOf(stage), taskId, stageLabel(stage));

        StageContentVO vo = new StageContentVO();
        vo.setFileResultId(fileResultId);
        vo.setStage(stage);
        vo.setLatest(false);
        vo.setItems(new ArrayList<>());
        if (ObjectUtil.isNull(task)) {
            return vo;
        }
        vo.setTaskId(task.getId());
        // latest = 该次运行产物内容是否可用：按 task.productId 精确取产物（历史任务同样可展示自己的内容）
        KbPipelineProduct product = ObjectUtil.isNull(task) || task.getProductId() == null ? null
                : pipelineProductDbService.getById(task.getProductId());
        if (ObjectUtil.isNull(product) || StrUtil.isBlank(product.getArtifactId())) {
            return vo;
        }
        vo.setLatest(true);

        try {
            vo.setItems(buildItems(stage, product.getArtifactId()));
        } catch (Exception e) {
            log.warn("产物内容读取失败, fileResultId={}, stage={}, artifactId={}",
                    fileResultId, stage, product.getArtifactId(), e);
            vo.setItems(new ArrayList<>());
        }
        return vo;
    }

    private List<StageContentItemVO> buildItems(String stage, String artifactId) {
        return switch (stage) {
            case "PARSE" -> parseItems(artifactId);
            case "STRUCTURE" -> structureItems(artifactId);
            case "PREPROCESS" -> preprocessItems(artifactId);
            case "CHUNK" -> chunkItemsBySet(chunkSetDbService.getByArtifactId(artifactId));
            case "EMBED" -> embedItemsBySet(embeddingSetDbService.getByArtifactId(artifactId));
            default -> List.of();
        };
    }

    // ---------------- 解析：sources 平铺元素 ----------------

    private List<StageContentItemVO> parseItems(String artifactId) {
        ParseResult result = readArtifact(artifactId, ParseResult.class);
        List<StageContentItemVO> items = new ArrayList<>();
        if (ObjectUtil.isNull(result) || ObjectUtil.isNull(result.getSources())) {
            return items;
        }
        int seq = 1;
        for (ParseSource source : result.getSources()) {
            for (ParseElement element : ObjectUtil.defaultIfNull(source.getElements(), List.<ParseElement>of())) {
                StageContentItemVO item = new StageContentItemVO();
                item.setAlignKey(element.getId());
                item.setSeq(seq++);
                item.setType(element.getType());
                item.setDisplay(element.getText());
                item.setExtra(parseExtra(source, element));
                items.add(item);
            }
        }
        return items;
    }

    /** 解析环节专属字段 + 公共页网格字段 */
    private Map<String, Object> parseExtra(ParseSource source, ParseElement element) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("source", source.getSource());
        extra.put("provider", source.getProvider());
        putPageGrid(extra, element.getPage(), element.getRows(), element.getCols());
        return extra;
    }

    // ---------------- 组装：章节树元素 ----------------

    private List<StageContentItemVO> structureItems(String artifactId) {
        UnifiedDocument document = readArtifact(artifactId, UnifiedDocument.class);
        List<StageContentItemVO> items = new ArrayList<>();
        if (ObjectUtil.isNull(document) || ObjectUtil.isNull(document.getElements())) {
            return items;
        }
        int seq = 1;
        for (UnifiedElement element : document.getElements()) {
            StageContentItemVO item = new StageContentItemVO();
            item.setAlignKey(element.getId());
            item.setSeq(seq++);
            item.setType(element.getType());
            item.setStatus(element.getConflictStatus());
            item.setDisplay(element.getText());
            item.setExtra(structureExtra(element));
            items.add(item);
        }
        return items;
    }

    /** 组装环节专属字段 + 公共页网格字段 */
    private Map<String, Object> structureExtra(UnifiedElement element) {
        Map<String, Object> extra = new LinkedHashMap<>();
        if (element.getLevel() != null) {
            extra.put("level", element.getLevel());
        }
        putPageGrid(extra, element.getPage(), element.getRows(), element.getCols());
        return extra;
    }

    /** 页/行/列公共字段按非空并入 extra（解析/组装共用口径） */
    private void putPageGrid(Map<String, Object> extra, Integer page, Integer rows, Integer cols) {
        if (page != null) {
            extra.put("page", page);
        }
        if (rows != null) {
            extra.put("rows", rows);
        }
        if (cols != null) {
            extra.put("cols", cols);
        }
    }

    // ---------------- 预处理：视图元素（display + normalized + 状态） ----------------

    private List<StageContentItemVO> preprocessItems(String artifactId) {
        PreprocessView view = readArtifact(artifactId, PreprocessView.class);
        List<StageContentItemVO> items = new ArrayList<>();
        if (ObjectUtil.isNull(view) || ObjectUtil.isNull(view.getElements())) {
            return items;
        }
        int seq = 1;
        for (ViewElement element : view.getElements()) {
            StageContentItemVO item = new StageContentItemVO();
            item.setAlignKey(element.getElementId());
            item.setSeq(seq++);
            item.setType(element.getType());
            item.setStatus(element.getStatus());
            item.setDisplay(element.getDisplayText());
            item.setNormalized(element.getNormalizedText());
            Map<String, Object> extra = new LinkedHashMap<>();
            if (element.getNormalizedFields() != null && !element.getNormalizedFields().isEmpty()) {
                extra.put("normalizedFields", JsonUtil.toJsonStr(element.getNormalizedFields()));
            }
            if (element.getCells() != null && !element.getCells().isEmpty()) {
                extra.put("cells", JsonUtil.toJsonStr(element.getCells()));
            }
            if (element.getPage() != null) {
                extra.put("page", element.getPage());
            }
            item.setExtra(extra);
            items.add(item);
        }
        return items;
    }

    // ---------------- 切片：kb_chunk 内容（按该次运行产物 artifactId 精确取集合） ----------------

    private List<StageContentItemVO> chunkItemsBySet(KbChunkSet chunkSet) {
        List<StageContentItemVO> items = new ArrayList<>();
        if (ObjectUtil.isNull(chunkSet)) {
            return items;
        }
        int seq = 1;
        for (KbChunk chunk : chunkDbService.listByChunkSetId(chunkSet.getId())) {
            items.add(itemOf(seq, chunk.getContentType(), null, chunk.getContent(), chunkExtra(chunk)));
            seq++;
        }
        return items;
    }

    // ---------------- 向量化：记录列表（按该次运行产物 artifactId 精确取集合） ----------------

    private List<StageContentItemVO> embedItemsBySet(KbEmbeddingSet embeddingSet) {
        List<StageContentItemVO> items = new ArrayList<>();
        if (ObjectUtil.isNull(embeddingSet)) {
            return items;
        }
        int seq = 1;
        for (KbEmbeddingRecord record : embeddingRecordDbService.listByEmbeddingSetId(embeddingSet.getId())) {
            items.add(itemOf(seq, record.getContentType(), record.getStatus(), record.getInputText(),
                    embedExtra(record)));
            seq++;
        }
        return items;
    }

    /** 切片/向量化共用条目组装：对齐键取顺序号 */
    private StageContentItemVO itemOf(int seq, String type, String status, String display,
                                      Map<String, Object> extra) {
        StageContentItemVO item = new StageContentItemVO();
        item.setAlignKey(String.valueOf(seq));
        item.setSeq(seq);
        item.setType(type);
        item.setStatus(status);
        item.setDisplay(display);
        item.setExtra(extra);
        return item;
    }

    /** 切片环节专属字段 */
    private Map<String, Object> chunkExtra(KbChunk chunk) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("chunkId", chunk.getChunkId());
        extra.put("titlePath", chunk.getTitlePath());
        extra.put("charCount", chunk.getCharCount());
        extra.put("tokenCount", chunk.getTokenCount());
        extra.put("parentChunkId", chunk.getParentChunkId());
        return extra;
    }

    /** 向量化环节专属字段 */
    private Map<String, Object> embedExtra(KbEmbeddingRecord record) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("chunkId", record.getChunkId());
        extra.put("tokenCount", record.getTokenCount());
        extra.put("cacheHit", record.getCacheHit());
        extra.put("requestId", record.getRequestId());
        return extra;
    }

    private <T> T readArtifact(String artifactId, Class<T> clazz) {
        byte[] content = fileStorage.getObject(artifactId);
        if (ObjectUtil.isNull(content)) {
            return null;
        }
        return JsonUtil.toObject(new String(content, StandardCharsets.UTF_8), clazz);
    }

    private PipelineStage stageOf(String stage) {
        return PipelineStage.valueOf(stage);
    }

    private String stageLabel(String stage) {
        return switch (stage) {
            case "PARSE" -> "解析";
            case "STRUCTURE" -> "组装";
            case "PREPROCESS" -> "预处理";
            case "CHUNK" -> "切片";
            case "EMBED" -> "向量化";
            default -> stage;
        };
    }
}
