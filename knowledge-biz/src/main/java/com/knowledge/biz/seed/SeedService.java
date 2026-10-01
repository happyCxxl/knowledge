package com.knowledge.biz.seed;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.knowledge.biz.service.IndexSetService;
import com.knowledge.biz.service.db.KbChunkDbService;
import com.knowledge.biz.service.db.KbChunkSetDbService;
import com.knowledge.biz.service.db.KbEmbeddingRecordDbService;
import com.knowledge.biz.service.db.KbEmbeddingSetDbService;
import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.biz.service.db.KbSourceFileDbService;
import com.knowledge.biz.service.support.EmbedRowSupport;
import com.knowledge.biz.task.ProductPersistence;
import com.knowledge.common.domain.chunk.Chunk;
import com.knowledge.common.domain.chunk.ChunkSet;
import com.knowledge.common.domain.embed.EmbeddingRecord;
import com.knowledge.common.domain.embed.EmbeddingSet;
import com.knowledge.common.domain.entity.KbChunk;
import com.knowledge.common.domain.entity.KbChunkSet;
import com.knowledge.common.domain.entity.KbEmbeddingRecord;
import com.knowledge.common.domain.entity.KbEmbeddingSet;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.entity.KbSourceFile;
import com.knowledge.common.domain.input.FileReference;
import com.knowledge.common.domain.parse.ParseElement;
import com.knowledge.common.domain.parse.ParseResult;
import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.domain.preprocess.PreprocessView;
import com.knowledge.common.domain.preprocess.ViewElement;
import com.knowledge.common.domain.structure.DocumentInfo;
import com.knowledge.common.domain.structure.UnifiedDocument;
import com.knowledge.common.domain.structure.UnifiedElement;
import com.knowledge.common.enums.embed.EmbedRecordStatus;
import com.knowledge.common.enums.embed.EmbeddingModel;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.enums.task.RowStatus;
import com.knowledge.filecenter.service.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示数据种子：为指定知识库造一条**从文件上传到向量化 + 索引构建**的完整链路。
 *
 * <p><b>为什么需要它</b>：向量化要经 {@code ModelGatewayPort} 调远程向量模型，模型未接入时
 * 整条链路断在 EMBED。本类用"直插数据 + 复用生产代码"的方式补出这条链路。
 *
 * <p><b>真实与模拟的边界</b>（诚实口径）：
 * <ul>
 *   <li><b>真实</b>：切片文本（取自 {@code kb_chunk} 表里那 152 片，源于真实 PDF）；
 *       产物序列化与落库（走 {@code ProductPersistence} 与领域对象，格式与生产一致）；
 *       索引构建（直接调 {@code IndexSetService.onFileProductsReady}，是生产代码）；</li>
 *   <li><b>模拟</b>：向量本体（{@link FakeVectorGenerator} 确定性生成，<b>无语义</b>，
 *       所以向量检索的排序没有语义意义）；PARSE/STRUCTURE 产物（由切片文本反推，
 *       不是真解析器/组装器跑的）。</li>
 * </ul>
 *
 * <p><b>产物写在哪</b>：跟随 {@code file-center.storage-type}；当前为 {@code local}。
 *
 * @author cxxl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeedService {

    /** 复用已有切片的来源：默认库里那份真实 PDF 的切片集 */
    private static final Long SOURCE_FILE_RESULT_ID = 2103824816453214209L;
    private static final String SOURCE_CHUNK_STRATEGY = "chunk-window-v1";

    /** 各环节策略版本串（要与知识库绑定的策略一致，索引构建的组合快照靠它比对） */
    private static final String PREPROCESS_VERSION = "preproc-keep-toc-v1";
    private static final String CHUNK_VERSION = "chunk-window-v1";
    private static final String EMBED_VERSION = "embed-nocache-v1";

    /** 父片类型：本库 EMBED 策略 includeParent=OFF，父片不向量化（与 EmbedPipeline 同口径） */
    private static final String SECTION_TYPE = "SECTION";

    /** 种子数据的固定 ID 基数（kb_index_* 无自增，必须显式传 id） */
    private static final long SEED_BASE = 2105000000000000100L;

    private final FileStorage fileStorage;
    private final ProductPersistence productPersistence;
    private final IndexSetService indexSetService;
    private final KbSourceFileDbService sourceFileDbService;
    private final KbFileResultDbService fileResultDbService;
    private final KbPipelineTaskDbService pipelineTaskDbService;
    private final KbPipelineProductDbService pipelineProductDbService;
    private final KbChunkSetDbService chunkSetDbService;
    private final KbChunkDbService chunkDbService;
    private final KbEmbeddingSetDbService embeddingSetDbService;
    private final KbEmbeddingRecordDbService embeddingRecordDbService;

    /**
     * 为新知识库造一条完整链路。
     *
     * @param knowledgeBaseId 目标知识库（需已绑定 PREPROCESS/CHUNK/EMBED 三条策略）
     * @return 本次种子的关键 ID
     */
    public Map<String, Object> seed(Long knowledgeBaseId) {
        // ① 真实切片：从 kb_chunk 表读（产物在 MinIO 而当前后端是 local，跨后端读不到）
        KbChunkSet sourceSet = locateSourceChunkSet();
        List<KbChunk> chunks = chunkDbService.listByChunkSetId(sourceSet.getId());
        if (chunks.isEmpty()) {
            throw new IllegalStateException("来源切片行不存在: chunkSetId=" + sourceSet.getId());
        }
        log.info("===> 种子：读入真实切片 {} 片（来源 chunkSetId={}）", chunks.size(), sourceSet.getId());

        // ② 文件链路
        KbFileResult origin = fileResultDbService.getById(SOURCE_FILE_RESULT_ID);
        KbSourceFile sourceFile = sourceFileDbService.getById(origin.getSourceFileId());
        KbSourceFile newSource = sourceFile;
        KbFileResult fileResult = newFileResult(knowledgeBaseId, newSource, origin);
        fileResultDbService.save(fileResult);
        log.info("===> 种子：新建文件结果 fileResultId={}", fileResult.getId());

        // ③ PARSE → STRUCTURE → PREPROCESS → CHUNK：产物由切片文本反推（模拟）
        StageProducts chain = buildUpstream(fileResult, newSource, chunks);

        // ④ EMBED：确定性向量
        EmbedOutcome embed = persistEmbed(fileResult, chain.chunk(), chunks, chain.chunkSetId());

        // ⑤ BUILD_INDEX：生产入口（注册组合 → 建 Milvus 集合 → 写向量 → 对账 → 发布判定）
        indexSetService.onFileProductsReady(fileResult.getId());
        log.info("===> 种子：已触发索引构建 fileResultId={}", fileResult.getId());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("knowledgeBaseId", knowledgeBaseId);
        summary.put("fileResultId", String.valueOf(fileResult.getId()));
        summary.put("sourceFileId", String.valueOf(newSource.getId()));
        summary.put("parseProductId", String.valueOf(chain.parse().getId()));
        summary.put("structureProductId", String.valueOf(chain.structure().getId()));
        summary.put("preprocessProductId", String.valueOf(chain.preprocess().getId()));
        summary.put("chunkProductId", String.valueOf(chain.chunk().getId()));
        summary.put("chunkSetId", String.valueOf(chain.chunkSetId()));
        summary.put("embeddingProductId", String.valueOf(embed.productId()));
        summary.put("embeddingSetId", String.valueOf(embed.setId()));
        summary.put("recordCount", chunks.size());
        summary.put("dimension", embed.dimension());
        summary.put("artifactStore", "file-center(storage-type 决定；当前为 local)");
        return summary;
    }

    // ---------------- 上游四阶段（产物由切片文本反推） ----------------

    /** 依次落 PARSE / STRUCTURE / PREPROCESS / CHUNK，返回四张产物行与切片集 ID */
    private StageProducts buildUpstream(KbFileResult fileResult, KbSourceFile sourceFile,
                                        List<KbChunk> chunks) {
        String documentId = "doc-" + fileResult.getId();

        // PARSE
        ParseResult parseResult = new ParseResult();
        parseResult.setResultId(fileResult.getId());
        FileReference ref = new FileReference();
        ref.setFileId(sourceFile.getFileId());
        ref.setFileName(sourceFile.getFileName());
        ref.setSha256(sourceFile.getSha256());
        ref.setMimeType(sourceFile.getMimeType());
        parseResult.setFile(ref);
        ParseSource parseSource = new ParseSource();
        parseSource.setSource("native");
        parseSource.setProvider("seed");
        parseSource.setElements(new ArrayList<>(chunks.stream().map(SeedService::toParseElement).toList()));
        parseResult.setSources(List.of(parseSource));
        KbPipelineProduct parse = productPersistence.persist(
                newTask(fileResult, PipelineStage.PARSE, null, null),
                PipelineStage.PARSE, null, capabilityOf("PARSE", "seed", "1.0"), parseResult);

        // STRUCTURE：元素 type 一律 PARAGRAPH（不造 TITLE，避免虚构章节层级）
        UnifiedDocument document = new UnifiedDocument();
        DocumentInfo info = new DocumentInfo();
        info.setDocumentId(documentId);
        info.setSourceFileRef(sourceFile.getFileId());
        info.setSourceFileType(sourceFile.getMimeType());
        info.setSchemaVersion(UnifiedDocument.SCHEMA_VERSION);
        document.setDocumentInfo(info);
        document.setElements(new ArrayList<>(chunks.stream().map(c -> toUnifiedElement(c)).toList()));
        KbPipelineProduct structure = productPersistence.persist(
                newTask(fileResult, PipelineStage.STRUCTURE, parse.getId(), null),
                PipelineStage.STRUCTURE, parse.getId(), "{\"schemaVersion\":\"1.0.0\"}", document);

        // PREPROCESS：不做任何改写（normalizedText == rawText），但产物必须是结构合法的 PreprocessView
        PreprocessView view = new PreprocessView();
        view.setViewId("pv-" + documentId + "-" + PREPROCESS_VERSION);
        view.setDocumentId(documentId);
        view.setFileResultId(fileResult.getId());
        view.setSourceFileRef(sourceFile.getFileId());
        view.setUpstreamProductRef(structure.getId());
        view.setStrategyVersion(PREPROCESS_VERSION);
        view.setOptions(Map.of());
        view.setElements(new ArrayList<>(chunks.stream().map(c -> toViewElement(c)).toList()));
        KbPipelineProduct preprocess = productPersistence.persist(
                newTask(fileResult, PipelineStage.PREPROCESS, structure.getId(), preprocessSnapshot()),
                PipelineStage.PREPROCESS, structure.getId(), preprocessSnapshot(), view);

        // CHUNK：直接沿用真实切片内容与 chunkId
        Long chunkSetId = IdWorker.getId();
        ChunkSet chunkSet = new ChunkSet();
        chunkSet.setChunkSetId("cs-" + documentId + "-" + CHUNK_VERSION);
        chunkSet.setFileResultId(fileResult.getId());
        chunkSet.setDocumentId(documentId);
        chunkSet.setStrategyVersion(CHUNK_VERSION);
        chunkSet.setUpstreamProductRef(preprocess.getId());
        chunkSet.setChunks(new ArrayList<>(chunks.stream().map(SeedService::toChunk).toList()));
        chunkSet.setChunkCount(chunkSet.getChunks().size());
        KbPipelineProduct chunk = productPersistence.persist(
                newTask(fileResult, PipelineStage.CHUNK, preprocess.getId(), chunkSnapshot()),
                PipelineStage.CHUNK, preprocess.getId(), chunkSnapshot(), chunkSet);

        // kb_chunk_set 行：索引构建靠 chunk_set_ref 找上游，必须落
        KbChunkSet setRow = new KbChunkSet();
        setRow.setId(chunkSetId);
        setRow.setFileResultId(fileResult.getId());
        setRow.setUpstreamProductId(preprocess.getId());
        setRow.setChunkStrategyVersion(CHUNK_VERSION);
        setRow.setChunkCount(chunkSet.getChunkCount());
        setRow.setTotalChars(chunks.stream().mapToInt(c -> lengthOf(c.getContent())).sum());
        setRow.setStatus(RowStatus.ACTIVE.name());
        setRow.setArtifactId(chunk.getArtifactId());
        setRow.setCreateTime(LocalDateTime.now());
        chunkSetDbService.save(setRow);

        // kb_chunk 行：切片详情页读它
        List<KbChunk> chunkRows = new ArrayList<>(chunks.size());
        int order = 1;
        for (KbChunk source : chunks) {
            KbChunk row = new KbChunk();
            row.setId(IdWorker.getId());
            row.setChunkSetId(chunkSetId);
            row.setChunkId(source.getChunkId());
            row.setParentChunkId(source.getParentChunkId());
            row.setContent(source.getContent());
            row.setContentType(source.getContentType());
            row.setTitlePath(source.getTitlePath());
            row.setPageRange(source.getPageRange());
            row.setTableRef(source.getTableRef());
            row.setOrderNo(order++);
            row.setCharCount(lengthOf(source.getContent()));
            row.setTokenCount(source.getTokenCount());
            row.setStrategyVersion(CHUNK_VERSION);
            row.setCreateTime(LocalDateTime.now());
            chunkRows.add(row);
        }
        chunkDbService.saveBatch(chunkRows, 500);

        return new StageProducts(parse, structure, preprocess, chunk, chunkSetId);
    }

    /** EMBED：确定性向量 + 产物 + 集合行 + 记录行 */
    private EmbedOutcome persistEmbed(KbFileResult fileResult, KbPipelineProduct chunkProduct,
                                     List<KbChunk> chunks, Long chunkSetRef) {
        EmbeddingModel model = EmbeddingModel.firstEnabled();

        EmbeddingSet set = new EmbeddingSet();
        set.setEmbeddingSetId("es-" + chunkSetRef + "-" + EMBED_VERSION);
        set.setFileResultId(fileResult.getId());
        set.setChunkSetRef(chunkSetRef);
        set.setChunkSetId("cs-doc-" + fileResult.getId() + "-" + CHUNK_VERSION);
        set.setStrategyVersion(EMBED_VERSION);
        set.setModel(model.key());
        set.setDimension(model.dimension());
        set.setMetric(model.metric().name());
        set.setNormalized(model.normalized());
        set.setRecordCount(chunks.size());
        set.setCachedCount(0);

        List<EmbeddingRecord> records = new ArrayList<>(chunks.size());
        int index = 1;
        for (KbChunk chunk : chunks) {
            String text = chunk.getContent() == null ? "" : chunk.getContent();
            EmbeddingRecord record = new EmbeddingRecord();
            record.setEmbeddingId(String.format("emb-%04d", index++));
            record.setChunkId(chunk.getChunkId());
            record.setContentType(chunk.getContentType());
            record.setParentChunkId(chunk.getParentChunkId());
            record.setInputText(text);
            record.setInputTextHash(FakeVectorGenerator.hash(text));
            record.setTokenCount((int) Math.ceil(text.length() / 1.5));

            /*
             * 父片（SECTION）跳过 —— 与生产 EmbedPipeline 同口径：
             * {@code if (SECTION.equals(contentType) && !strategy.includeParentOn()) markSkipped(...)}
             * 本库绑定的 embed-nocache 策略 config 里 includeParent=OFF，所以父片只进
             * kb_chunk（供父子检索做上下文扩展），不生成向量、不进 Milvus。
             *
             * **这条规则不能省**：父片是整章内容，实测最长 22572 字符，而 Milvus 的
             * content 列硬上限 8192 —— 漏掉这一跳会让整个追加批次 upsert 失败。
             */
            if (SECTION_TYPE.equals(chunk.getContentType())) {
                record.setStatus(EmbedRecordStatus.SKIPPED.name());
                record.setCacheHit(false);
                records.add(record);
                continue;
            }

            record.setStatus(EmbedRecordStatus.SUCCESS.name());
            record.setCacheHit(false);
            record.setVector(FakeVectorGenerator.generate(text, model.dimension()));
            records.add(record);
        }
        set.setRecords(records);
        // 记录数含复用/跳过（与生产口径一致：SKIPPED 也计入 records）
        set.setRecordCount(records.size());
        set.setCachedCount(0);

        KbPipelineProduct product = productPersistence.persist(
                newTask(fileResult, PipelineStage.EMBED, chunkProduct.getId(), embedSnapshot()),
                PipelineStage.EMBED, chunkProduct.getId(), embedSnapshot(), set);

        KbEmbeddingSet setRow = EmbedRowSupport.setRow(fileResult.getId(), set, EMBED_VERSION,
                product.getArtifactId());
        setRow.setId(IdWorker.getId());
        setRow.setCreateTime(LocalDateTime.now());
        embeddingSetDbService.save(setRow);

        List<KbEmbeddingRecord> recordRows = new ArrayList<>(records.size());
        for (EmbeddingRecord record : records) {
            KbEmbeddingRecord row = EmbedRowSupport.recordRow(setRow.getId(), record);
            row.setId(IdWorker.getId());
            row.setCacheHit(false);
            row.setCreateTime(LocalDateTime.now());
            recordRows.add(row);
        }
        embeddingRecordDbService.saveBatch(recordRows, 500);

        return new EmbedOutcome(setRow.getId(), product.getId(), model.dimension());
    }

    // ---------------- 领域对象构造 ----------------

    private static ParseElement toParseElement(KbChunk chunk) {
        ParseElement element = new ParseElement();
        element.setId(chunk.getChunkId());
        element.setType("PARAGRAPH");
        element.setText(chunk.getContent());
        element.setPage(firstPageOf(chunk));
        return element;
    }

    private static UnifiedElement toUnifiedElement(KbChunk chunk) {
        UnifiedElement element = new UnifiedElement();
        // 元素 ID 与切片来源保持一致，切片溯源链（sourceElementIds）才有意义
        element.setId(chunk.getChunkId());
        element.setType("PARAGRAPH");
        element.setText(chunk.getContent());
        element.setPage(firstPageOf(chunk));
        return element;
    }

    private static ViewElement toViewElement(KbChunk chunk) {
        ViewElement element = new ViewElement();
        element.setElementId(chunk.getChunkId());
        element.setType("PARAGRAPH");
        element.setStatus("NORMAL");
        element.setPage(firstPageOf(chunk));
        element.setRawText(chunk.getContent());
        element.setDisplayText(chunk.getContent());
        // 预处理不做改写：检索口径与原文一致
        element.setNormalizedText(chunk.getContent());
        return element;
    }

    private static Chunk toChunk(KbChunk row) {
        Chunk chunk = new Chunk();
        chunk.setChunkId(row.getChunkId());
        chunk.setParentChunkId(row.getParentChunkId());
        chunk.setContent(row.getContent());
        chunk.setContentType(row.getContentType() == null ? "PARAGRAPH" : row.getContentType());
        // 空串而不是 null：Milvus 的 BM25 函数要求 content/title_path 两个输入字段非空，
        // 传 null 会让建集合直接失败（实测：function input field cannot be nullable）
        chunk.setTitlePath(row.getTitlePath() == null ? "" : row.getTitlePath());
        chunk.setSourceElementIds(List.of(row.getChunkId()));
        chunk.setOrder(row.getOrderNo() == null ? 0 : row.getOrderNo());
        chunk.setCharCount(lengthOf(row.getContent()));
        chunk.setTokenCount(row.getTokenCount() == null ? 0 : row.getTokenCount());
        return chunk;
    }

    private static Integer firstPageOf(KbChunk chunk) {
        String range = chunk.getPageRange();
        if (range == null || range.isBlank()) {
            return 1;
        }
        // 存的是 JSON 数组文本（如 "[1,2,3]"）或 "1-3"；只取首个数字，取不到就回落 1
        // 用 parseInt 而非 valueOf：后者先装箱成 Integer 再拆箱（SpotBugs DM_BOXED_PRIMITIVE_FOR_PARSING）
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+").matcher(range);
        return matcher.find() ? Integer.parseInt(matcher.group()) : 1;
    }

    // ---------------- 策略/能力快照 ----------------

    private String preprocessSnapshot() {
        // resolveProductCombo 会把它反序列化成 PreprocessStrategy 并取 fullVersion()，
        // 所以 name/version 必须与知识库绑定的策略一致
        return "{\"type\":\"PREPROCESS\",\"name\":\"preproc-keep-toc\",\"version\":\"v1\","
                + "\"rules\":{\"tidy\":{\"enabled\":\"ON\",\"params\":{\"whitespace\":\"ON\"}}},"
                + "\"custom\":{\"enabled\":\"ON\",\"rules\":[]}}";
    }

    private String chunkSnapshot() {
        return "{\"type\":\"CHUNK\",\"name\":\"chunk-window\",\"version\":\"v1\"}";
    }

    private String embedSnapshot() {
        return "{\"type\":\"EMBED\",\"name\":\"embed-nocache\",\"version\":\"v1\","
                + "\"model\":\"" + EmbeddingModel.firstEnabled().key() + "\"}";
    }

    private static String capabilityOf(String type, String name, String version) {
        return "{\"type\":\"" + type + "\",\"name\":\"" + name + "\",\"version\":\"" + version + "\"}";
    }

    // ---------------- 文件链路 ----------------

    private KbChunkSet locateSourceChunkSet() {
        List<KbChunkSet> sets = chunkSetDbService.listByFileResultId(SOURCE_FILE_RESULT_ID);
        return sets.stream()
                .filter(s -> SOURCE_CHUNK_STRATEGY.equals(s.getChunkStrategyVersion()))
                .reduce((first, second) -> second)
                .orElseThrow(() -> new IllegalStateException("找不到来源切片集: fileResultId="
                        + SOURCE_FILE_RESULT_ID + ", strategy=" + SOURCE_CHUNK_STRATEGY));
    }

    private KbFileResult newFileResult(Long knowledgeBaseId, KbSourceFile source, KbFileResult origin) {
        KbFileResult row = new KbFileResult();
        // 用雪花 ID（与生产一致）而不是固定 ID：kb_file_result 是逻辑删除表，
        // 重置只把 del_flag 置 1、物理行仍在，固定 ID 重跑必撞主键
        row.setId(IdWorker.getId());
        row.setKnowledgeBaseId(knowledgeBaseId);
        row.setOwner(origin.getOwner());
        row.setSourceFileId(source.getId());
        row.setUserId(source.getUserId());
        row.setDelFlag("0");
        row.setCreateBy("ADMIN");
        row.setCreateTime(LocalDateTime.now());
        return row;
    }

    /** 建一条已成功的任务行（种子不经过队列，直接落终态） */
    private KbPipelineTask newTask(KbFileResult fileResult, PipelineStage stage,
                                   Long upstreamProductId, String strategySnapshot) {
        KbPipelineTask task = new KbPipelineTask();
        task.setId(IdWorker.getId());
        task.setFileResultId(fileResult.getId());
        task.setStage(stage.name());
        task.setUpstreamProductId(upstreamProductId);
        task.setStrategySnapshot(strategySnapshot);
        task.setStatus(PipelineTaskStatus.SUCCESS.name());
        task.setRetryCount(0);
        task.setCreateTime(LocalDateTime.now());
        task.setStartedAt(LocalDateTime.now());
        task.setFinishedAt(LocalDateTime.now());
        pipelineTaskDbService.save(task);
        return task;
    }

    private static int lengthOf(String text) {
        return text == null ? 0 : text.length();
    }

    /** 上游四阶段的产物行与切片集 ID */
    private record StageProducts(KbPipelineProduct parse, KbPipelineProduct structure,
                                 KbPipelineProduct preprocess, KbPipelineProduct chunk,
                                 Long chunkSetId) {
    }

    /** 向量化的产物行、集合行与维度 */
    private record EmbedOutcome(Long setId, Long productId, int dimension) {
    }
}
