package com.knowledge.biz.seed;

import com.knowledge.biz.service.db.KbChunkDbService;
import com.knowledge.biz.service.db.KbChunkSetDbService;
import com.knowledge.biz.service.db.KbEmbeddingRecordDbService;
import com.knowledge.biz.service.db.KbEmbeddingSetDbService;
import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbIndexSetDbService;
import com.knowledge.biz.service.db.KbIndexVersionDbService;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.biz.service.db.KbSourceFileDbService;
import com.knowledge.common.domain.entity.KbChunk;
import com.knowledge.common.domain.entity.KbChunkSet;
import com.knowledge.common.domain.entity.KbEmbeddingRecord;
import com.knowledge.common.domain.entity.KbEmbeddingSet;
import com.knowledge.common.domain.entity.KbIndexSet;
import com.knowledge.common.domain.entity.KbIndexVersion;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.entity.KbSourceFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 种子数据的重置：把某个知识库下**由种子产生**的数据删干净，让 {@link SeedService} 可重复执行。
 *
 * <p><b>只删种子产生的行</b>，判据是文件结果 ID 落在种子 ID 段内（{@code 2105...}）。
 * 不做"按知识库全删"—— 那样会连带删掉真实数据。
 *
 * <p><b>不删 Milvus 集合</b>：集合名是 {@code kb_{kbId}_{versionNo}}，删库版本行后
 * 重跑会新建集合；旧集合的清理属于索引回收流程（{@code IndexSetService.recycle}），
 * 这里不越界替它做。
 *
 * @author cxxl
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeedResetService {

    /**
     * 种子基准源文件：种子的 file_result 都指向它（与来源文件同一份真实文件档案）。
     *
     * <p>不用"ID 段"判据 —— 种子的 file_result 用雪花 ID（与生产一致），
     * 且 {@code kb_file_result} 是逻辑删除表（重置只置 del_flag、物理行仍在），
     * 固定 ID 重跑会撞主键。
     */
    private static final Long SEED_SOURCE_FILE_ID = 2103824816390299649L;

    private final KbFileResultDbService fileResultDbService;
    private final KbSourceFileDbService sourceFileDbService;
    private final KbPipelineTaskDbService pipelineTaskDbService;
    private final KbPipelineProductDbService pipelineProductDbService;
    private final KbChunkSetDbService chunkSetDbService;
    private final KbChunkDbService chunkDbService;
    private final KbEmbeddingSetDbService embeddingSetDbService;
    private final KbEmbeddingRecordDbService embeddingRecordDbService;
    private final KbIndexSetDbService indexSetDbService;
    private final KbIndexVersionDbService indexVersionDbService;

    /**
     * 删除指定知识库下由种子产生的全部行。
     *
     * @param knowledgeBaseId 目标知识库
     * @return 删除统计（各表行数）
     */
    public java.util.Map<String, Integer> reset(Long knowledgeBaseId) {
        // 判据：该知识库下、source_file 指向"种子基准文件"的 file_result。
        // 不用 ID 段判据 —— file_result 现在用雪花 ID（与生产一致）。
        KbSourceFile seedSource = sourceFileDbService.getById(SEED_SOURCE_FILE_ID);
        Long seedSourceFileId = seedSource == null ? null : seedSource.getId();
        List<Long> seedFileResultIds = fileResultDbService.list().stream()
                .filter(f -> knowledgeBaseId.equals(f.getKnowledgeBaseId()))
                .filter(f -> seedSourceFileId != null && seedSourceFileId.equals(f.getSourceFileId()))
                .map(KbFileResult::getId)
                .toList();

        java.util.Map<String, Integer> removed = new java.util.LinkedHashMap<>();
        for (Long fileResultId : seedFileResultIds) {
            for (KbChunkSet set : chunkSetDbService.listByFileResultId(fileResultId)) {
                List<KbChunk> chunks = chunkDbService.listByChunkSetId(set.getId());
                if (!chunks.isEmpty()) {
                    chunkDbService.removeByIds(chunks.stream().map(KbChunk::getId).toList());
                }
                chunkSetDbService.removeById(set.getId());
            }
            for (KbEmbeddingSet set : embeddingSetDbService.listByFileResultId(fileResultId)) {
                List<KbEmbeddingRecord> records = embeddingRecordDbService.listByEmbeddingSetId(set.getId());
                if (!records.isEmpty()) {
                    embeddingRecordDbService.removeByIds(records.stream().map(KbEmbeddingRecord::getId).toList());
                }
                embeddingSetDbService.removeById(set.getId());
            }
            List<KbPipelineProduct> products = pipelineProductDbService.list().stream()
                    .filter(p -> fileResultId.equals(p.getFileResultId())).toList();
            if (!products.isEmpty()) {
                pipelineProductDbService.removeByIds(products.stream().map(KbPipelineProduct::getId).toList());
            }
            List<KbPipelineTask> tasks = pipelineTaskDbService.listByFileResultId(fileResultId);
            if (!tasks.isEmpty()) {
                pipelineTaskDbService.removeByIds(tasks.stream().map(KbPipelineTask::getId).toList());
            }
            fileResultDbService.removeById(fileResultId);
            removed.merge("fileResult", 1, Integer::sum);
        }

        // 索引：知识库一库一行，删掉它连带它的版本行
        KbIndexSet indexSet = indexSetDbService.getByKb(knowledgeBaseId);
        if (indexSet != null) {
            List<KbIndexVersion> versions = indexVersionDbService.listByIndexSetId(indexSet.getId());
            if (!versions.isEmpty()) {
                indexVersionDbService.removeByIds(versions.stream().map(KbIndexVersion::getId).toList());
                removed.put("indexVersion", versions.size());
            }
            indexSetDbService.removeById(indexSet.getId());
            removed.put("indexSet", 1);
        }

        log.info("===> 种子重置完成: kb={}, 删除 {}", knowledgeBaseId, removed);
        return removed;
    }
}
