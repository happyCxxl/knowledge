package com.knowledge.biz.service.support;

import cn.hutool.core.util.ObjectUtil;
import com.knowledge.common.domain.chunk.Chunk;
import com.knowledge.common.domain.chunk.ChunkSet;
import com.knowledge.common.domain.embed.EmbeddingRecord;
import com.knowledge.common.domain.embed.EmbeddingSet;
import com.knowledge.common.domain.entity.KbChunkSet;
import com.knowledge.common.domain.entity.KbEmbeddingSet;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.filecenter.service.FileStorage;
import com.knowledge.worker.indexing.IndexRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 索引行装配器：单文件装配 IndexRow——向量产物 SUCCESS/CACHED 记录子集；content=record.inputText；
 * titlePath/sourceElementIds 自切片产物按 chunkId 关联。追加路径与批量构建共用。
 *
 * @author cxxl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexRowAssembler {

    private final FileStorage fileStorage;

    /**
     * 装配单文件索引行。
     *
     * @return 产物读取失败 → null（调用方按失败处置）；无 SUCCESS/CACHED 记录 → 空列表（调用方跳过）
     */
    public List<IndexRow> assemble(Long fileResultId, String owner, KbChunkSet chunkRow, KbEmbeddingSet embedRow) {
        EmbeddingSet embedSet = readEmbeddingSet(embedRow.getArtifactId());
        if (ObjectUtil.isNull(embedSet) || ObjectUtil.isNull(embedSet.getRecords())) {
            log.warn("===> IndexRowAssembler 向量产物缺失或记录为空, artifactId={}", embedRow.getArtifactId());
            return null;
        }
        Map<String, Chunk> chunkById = readChunkIndex(chunkRow.getArtifactId());
        List<IndexRow> rows = new ArrayList<>();
        for (EmbeddingRecord record : embedSet.getRecords()) {
            if (!"SUCCESS".equals(record.getStatus()) && !"CACHED".equals(record.getStatus())) {
                continue;
            }
            Chunk chunk = ObjectUtil.isNull(chunkById) ? null : chunkById.get(record.getChunkId());
            IndexRow row = new IndexRow();
            row.setChunkId(record.getChunkId());
            row.setDocumentId(fileResultId);
            row.setOwner(owner);
            row.setContentType(record.getContentType());
            row.setParentChunkId(record.getParentChunkId());
            row.setContent(record.getInputText());
            row.setTitlePath(ObjectUtil.isNull(chunk) ? null : chunk.getTitlePath());
            row.setSourceElementIds(ObjectUtil.isNull(chunk) || ObjectUtil.isNull(chunk.getSourceElementIds())
                    ? null : JsonUtil.toJsonStr(chunk.getSourceElementIds()));
            row.setVector(record.getVector());
            rows.add(row);
        }
        return rows;
    }

    /** 向量产物 artifact → EmbeddingSet（读取失败返回 null） */
    public EmbeddingSet readEmbeddingSet(String artifactId) {
        try {
            byte[] content = fileStorage.getObject(artifactId);
            if (ObjectUtil.isNull(content)) {
                return null;
            }
            return JsonUtil.toObject(new String(content, StandardCharsets.UTF_8), EmbeddingSet.class);
        } catch (Exception e) {
            log.warn("向量产物读取失败, artifactId={}", artifactId, e);
            return null;
        }
    }

    /** 切片产物 → chunkId 索引（读取失败返回 null，增强字段降级为空） */
    public Map<String, Chunk> readChunkIndex(String artifactId) {
        try {
            byte[] content = fileStorage.getObject(artifactId);
            if (ObjectUtil.isNull(content)) {
                return null;
            }
            ChunkSet chunkSet = JsonUtil.toObject(new String(content, StandardCharsets.UTF_8), ChunkSet.class);
            if (ObjectUtil.isNull(chunkSet) || ObjectUtil.isNull(chunkSet.getChunks())) {
                return null;
            }
            Map<String, Chunk> index = new HashMap<>();
            for (Chunk chunk : chunkSet.getChunks()) {
                index.put(chunk.getChunkId(), chunk);
            }
            return index;
        } catch (Exception e) {
            log.warn("切片产物读取失败, artifactId={}", artifactId, e);
            return null;
        }
    }
}
