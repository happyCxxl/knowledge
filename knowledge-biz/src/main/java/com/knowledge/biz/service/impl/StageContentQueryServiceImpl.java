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
import com.knowledge.biz.service.support.FileResultAccessGuard;
import com.knowledge.biz.service.support.TaskDetailSupport;
import com.knowledge.common.domain.entity.KbChunk;
import com.knowledge.common.domain.entity.KbChunkSet;
import com.knowledge.common.domain.entity.KbEmbeddingRecord;
import com.knowledge.common.domain.entity.KbEmbeddingSet;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.parse.BBox;
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

/**
 * 产物内容查询实现：
 * 白名单五环节；按 task.productId 精确取该次运行的产物内容（与详情接口同口径，历史任务同样可展示）；
 * 对齐键：PARSE/STRUCTURE/PREPROCESS=elementId、CHUNK/EMBED=顺序号。
 *
 * @author cxxl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StageContentQueryServiceImpl implements StageContentQueryService {

    private final KbFileResultDbService fileResultDbService;
    private final KbPipelineProductDbService pipelineProductDbService;
    private final KbChunkSetDbService chunkSetDbService;
    private final KbChunkDbService chunkDbService;
    private final KbEmbeddingSetDbService embeddingSetDbService;
    private final KbEmbeddingRecordDbService embeddingRecordDbService;
    private final FileStorage fileStorage;
    private final TaskDetailSupport detailSupport;
    private final FileResultAccessGuard accessGuard;

    @Override
    public StageContentVO stageContent(Long fileResultId, String stage, Long taskId, Long docPage,
                                       Integer page, Integer limit) {
        KbFileResult fileResult = fileResultDbService.getById(fileResultId);
        ThrowUtil.throwIf(ObjectUtil.isNull(fileResult), ErrorCode.FILE_RESULT_NOT_FOUND);
        accessGuard.check(fileResult);
        ThrowUtil.throwIf(StrUtil.isBlank(stage) || !PipelineStage.FILE_CHAIN_STAGES.contains(stage),
                ErrorCode.PARAM_INVALID, "未知环节: " + stage);
        KbPipelineTask task = detailSupport.resolveTask(fileResultId, stageOf(stage), taskId, stageLabel(stage));

        int pageNo = resolvePage(page);
        int pageSize = resolveLimit(limit);
        StageContentVO vo = new StageContentVO();
        vo.setFileResultId(fileResultId);
        vo.setStage(stage);
        vo.setLatest(false);
        vo.setDocPage(ObjectUtil.isNull(docPage) || docPage < 1 ? null : docPage.intValue());
        vo.setItems(new ArrayList<>());
        vo.setTotal(0);
        vo.setPage(pageNo);
        vo.setLimit(pageSize);
        vo.setTruncated(false);
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
            // 页码过滤先于分页：total 与翻页都按"该页元素"这一口径给
            List<StageContentItemVO> all = filterByDocPage(buildItems(stage, product.getArtifactId()), docPage);
            vo.setTotal(all.size());
            vo.setItems(slice(all, pageNo, pageSize));
        } catch (Exception e) {
            log.warn("产物内容读取失败, fileResultId={}, stage={}, artifactId={}",
                    fileResultId, stage, product.getArtifactId(), e);
            vo.setItems(new ArrayList<>());
            vo.setTotal(0);
        }
        // 截断 = 这一页之后还有内容（含页码越界）
        vo.setTruncated(hasMore(vo.getPage(), vo.getLimit(), vo.getItems().size(), vo.getTotal()));
        return vo;
    }

    /** 页码归一：非正数或空按第 1 页 */
    private int resolvePage(Integer page) {
        return ObjectUtil.isNull(page) || page < 1 ? 1 : page;
    }

    /** 每页条数归一：空/非正按默认上限，超过上限按上限截断 */
    private int resolveLimit(Integer limit) {
        if (ObjectUtil.isNull(limit) || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    /**
     * 这一页之后是否还有内容。
     *
     * <p>**不能写成 `page × limit < total`**：最后一页不满时（5 条按每页 2 条取第 3 页，
     * 只回 1 条）`3 × 2 = 6` 不小于 5，会把"已到末页"报成还有内容。按"这一页取到的位移
     * 是否到达总数"判断才等价于"后面还有没有"。
     *
     * @param page      当前页码
     * @param limit     本页条数上限
     * @param pageCount 本页实际返回条数
     * @param total     总条数
     * @return 还有内容未返回则为 true（页码越界为空页时同样为 true）
     */
    private boolean hasMore(int page, int limit, int pageCount, int total) {
        if (total <= 0) {
            return false;
        }
        return (page - 1) * limit + pageCount < total;
    }

    /** 取某一页：越界返回空列表（total 与 truncated 由调用处据实标明） */
    private List<StageContentItemVO> slice(List<StageContentItemVO> all, int page, int limit) {
        int from = (page - 1) * limit;
        if (from >= all.size()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(all.subList(from, Math.min(from + limit, all.size())));
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

    /** 解析环节专属字段 + 公共页网格字段 + 原文定位锚点 */
    private Map<String, Object> parseExtra(ParseSource source, ParseElement element) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("source", source.getSource());
        extra.put("provider", source.getProvider());
        putPageGrid(extra, element.getPage(), element.getRows(), element.getCols());
        putBbox(extra, element.getBbox());
        putOfficeAnchor(extra, element);
        return extra;
    }

    /**
     * Office 原文定位锚点：原文预览按这些字段把元素对到渲染结果上。
     *
     * <p>Word 段落给样式名（渲染器把样式名写在段落 DOM 上）；Excel 给工作表名与单元格行列
     * （行列是解析产物里的 0 基下标，与表格区域高亮同一口径）。
     */
    private void putOfficeAnchor(Map<String, Object> extra, ParseElement element) {
        if (StrUtil.isNotBlank(element.getStyle())) {
            extra.put("style", element.getStyle());
        }
        if (StrUtil.isNotBlank(element.getSheetName())) {
            extra.put("sheetName", element.getSheetName());
        }
        if (ObjectUtil.isNotNull(element.getRow())) {
            extra.put("row", element.getRow());
        }
        if (ObjectUtil.isNotNull(element.getCol())) {
            extra.put("col", element.getCol());
        }
        if (ObjectUtil.isNotNull(element.getRowSpan())) {
            extra.put("rowSpan", element.getRowSpan());
        }
        if (ObjectUtil.isNotNull(element.getColSpan())) {
            extra.put("colSpan", element.getColSpan());
        }
        if (ObjectUtil.isNotNull(element.getIsHeader())) {
            extra.put("isHeader", element.getIsHeader());
        }
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
        putBbox(extra, element.getBbox());
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

    /**
     * 边界框并入 extra：原文预览按它在页面上画高亮框。
     *
     * <p>单位点（pt）、左上角原点，与解析产物里的 bbox 同口径；元素无坐标时不下发该字段。
     */
    private void putBbox(Map<String, Object> extra, BBox bbox) {
        if (ObjectUtil.isNull(bbox)) {
            return;
        }
        Map<String, Object> box = new LinkedHashMap<>();
        box.put("x", bbox.getX());
        box.put("y", bbox.getY());
        box.put("width", bbox.getWidth());
        box.put("height", bbox.getHeight());
        extra.put("bbox", box);
    }

    /** 元素的文档页码：解析/组装走 extra.page，其余环节无页概念返回 null */
    private Long docPageOf(StageContentItemVO item) {
        Object value = ObjectUtil.isNull(item.getExtra()) ? null : item.getExtra().get("page");
        return value instanceof Number number ? number.longValue() : null;
    }

    /**
     * 按文档页过滤：只留该页元素（无页概念/非该页的一律排除）。
     *
     * <p>比较用 Long：入参来自 HTTP 是大整数，而元素页码在产物里是 Integer，
     * 直接比会因类型不同永远不相等。
     */
    private List<StageContentItemVO> filterByDocPage(List<StageContentItemVO> all, Long docPage) {
        if (ObjectUtil.isNull(docPage) || docPage < 1) {
            return all;
        }
        return all.stream()
                .filter(item -> ObjectUtil.equal(docPageOf(item), docPage))
                .toList();
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
