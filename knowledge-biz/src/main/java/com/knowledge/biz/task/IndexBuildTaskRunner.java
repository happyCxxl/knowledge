package com.knowledge.biz.task;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.biz.service.IndexSetService;
import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.biz.service.db.KbIndexVersionDbService;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.biz.service.db.KnowledgeBaseDbService;
import com.knowledge.biz.service.support.IndexComboReconciler;
import com.knowledge.biz.service.support.IndexRowAssembler;
import com.knowledge.common.domain.entity.KbChunkSet;
import com.knowledge.common.domain.entity.KbEmbeddingSet;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.common.domain.entity.KbIndexVersion;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.domain.entity.KnowledgeBase;
import com.knowledge.common.enums.index.IndexBuildTrigger;
import com.knowledge.common.enums.index.IndexVersionStatus;
import com.knowledge.common.enums.task.PipelineTaskErrorCode;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.worker.indexing.BuildOrder;
import com.knowledge.worker.indexing.ComboSnapshot;
import com.knowledge.worker.indexing.IndexRow;
import com.knowledge.worker.indexing.MilvusIndexPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * 索引构建任务执行器（step-13 B08，2026-09 定稿：策略集合模型，注册 + 对账语义；B8.1 增评测冻结集回填）：
 * 领任务 → 版本行 CREATED→BUILDING → ① 组合完整性/维度/血缘校验（IndexComboReconciler 实时重算，
 * 吸收并发追加）→ ② 集合生命周期（ensureCollection + 预热 load，幂等）→ ②.5 LIST 冻结集回填（范围文件 ×
 * 单一取数口径批量追加，幂等）→ ③ 全量对账（产物 chunkId == 集合 chunkId；失败保留集合可重试）→ ④ READY +
 * 活账本统计收敛 → ⑤ 发布判定（COMPENSATE 恒自动；INCREMENT 且绑定开启自动；LIST 永不自动；其余停留 READY）。
 * 行写入：ALL 组合走产物就绪回调（onFileProductsReady）追加；LIST 冻结集由本任务回填 + 回调范围感知路由。
 *
 * @author cxxl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexBuildTaskRunner {

    private final KbPipelineTaskDbService pipelineTaskDbService;
    private final KbIndexVersionDbService indexVersionDbService;
    private final KnowledgeBaseDbService knowledgeBaseDbService;
    private final KbFileResultDbService fileResultDbService;
    private final MilvusIndexPort milvusIndexPort;
    private final IndexSetService indexSetService;
    private final IndexComboReconciler indexComboReconciler;
    private final IndexRowAssembler indexRowAssembler;

    /** 执行单个索引构建任务（由消费循环提交，外层看门狗负责超时）。 */
    public void run(Long taskId) {
        KbPipelineTask task = pipelineTaskDbService.getById(taskId);
        if (ObjectUtil.isNull(task)
                || !PipelineTaskStatus.QUEUED.name().equals(task.getStatus())) {
            return;
        }
        if (pipelineTaskDbService.claim(taskId) != 1) {
            return;
        }
        try {
            KbIndexVersion version = indexVersionDbService.getByTaskId(taskId);
            if (ObjectUtil.isNull(version)) {
                finishFailed(taskId, PipelineTaskErrorCode.INDEX_BUILD_FAILED.name(), "索引版本行不存在");
                return;
            }
            BuildOrder order = JsonUtil.toObject(task.getStrategySnapshot(), BuildOrder.class);
            if (ObjectUtil.isNull(order) || ObjectUtil.isNull(order.getComboSnapshot())
                    || ObjectUtil.isNull(order.getKnowledgeBaseId())) {
                markFailed(version, "构建命令缺失");
                finishFailed(taskId, PipelineTaskErrorCode.INDEX_BUILD_FAILED.name(), "构建命令缺失");
                return;
            }
            ComboSnapshot combo = order.getComboSnapshot();
            Long kbId = order.getKnowledgeBaseId();
            version.setStatus(IndexVersionStatus.BUILDING.name());
            version.setBuildError(null);
            indexVersionDbService.updateById(version);

            // ① 完整性/维度/血缘校验 + expected（对账器实时重算，吸收并发追加）
            IndexComboReconciler.ComboExpectation expected = indexComboReconciler.computeExpected(kbId, combo);
            if (!expected.complete()) {
                markFailed(version, expected.gap());
                finishFailed(taskId, PipelineTaskErrorCode.INDEX_INCOMPLETE.name(), expected.gap());
                return;
            }
            if (!expected.dimConsistent()) {
                markFailed(version, expected.dimError());
                finishFailed(taskId, PipelineTaskErrorCode.INDEX_DIMENSION_MISMATCH.name(), expected.dimError());
                return;
            }

            // ② 集合生命周期（幂等 + 发布预热）
            String collectionName = MilvusIndexPort.collectionName(kbId, version.getVersionNo());
            try {
                milvusIndexPort.ensureCollection(collectionName, expected.dimension());
                milvusIndexPort.load(collectionName);
            } catch (Exception e) {
                markFailed(version, "集合初始化失败: " + truncate(String.valueOf(e.getMessage())));
                finishFailed(taskId, PipelineTaskErrorCode.INDEX_BUILD_FAILED.name(),
                        truncate(String.valueOf(e.getMessage())));
                return;
            }

            // ②.5 评测冻结集回填（B8.1）：LIST 版本的行写入不依赖追加回调（产物就绪事件已发生，需回补）
            if (combo.isListScope()) {
                backfillFrozenScope(combo, collectionName);
            }

            // ③ 全量对账：产物 chunkId == 集合 chunkId（失败保留集合可重试，不影响在线）
            List<String> actual = milvusIndexPort.listChunkIds(collectionName);
            if (!expected.chunkIds().equals(new HashSet<>(actual))) {
                String msg = "一致性校验失败: 产物 " + expected.chunkIds().size()
                        + " 片 vs Milvus " + actual.size() + " 片（保留集合可重试）";
                markFailed(version, msg);
                finishFailed(taskId, PipelineTaskErrorCode.INDEX_CONSISTENCY_FAILED.name(), msg);
                return;
            }

            // ④ READY + 活账本统计收敛（以对账时刻产物状态为准）
            version.setStatus(IndexVersionStatus.READY.name());
            version.setValidatedAt(LocalDateTime.now());
            version.setChunkCount(expected.chunkIds().size());
            version.setVectorCount(expected.vectorCount());
            version.setBuildError(null);
            indexVersionDbService.updateById(version);
            pipelineTaskDbService.finish(taskId, PipelineTaskStatus.SUCCESS.name(), null, null);

            // ⑤ 发布判定：COMPENSATE 恒自动；INCREMENT 且绑定开启自动；LIST 冻结集永不自动；其余停留 READY
            if (shouldAutoPublish(kbId, combo, order.getTrigger())) {
                try {
                    indexSetService.publish(version.getId());
                    log.info("===> IndexBuildTaskRunner 自动发布完成, kbId={}, versionNo={}",
                            kbId, version.getVersionNo());
                } catch (Exception e) {
                    log.warn("===> IndexBuildTaskRunner 自动发布失败，停留 READY 等管理员, versionId={}",
                            version.getId(), e);
                }
            }
        } catch (Exception e) {
            log.error("索引构建任务执行异常, taskId={}", taskId, e);
            try {
                KbIndexVersion version = indexVersionDbService.getByTaskId(taskId);
                if (ObjectUtil.isNotNull(version)
                        && !IndexVersionStatus.READY.name().equals(version.getStatus())
                        && !IndexVersionStatus.ONLINE.name().equals(version.getStatus())) {
                    markFailed(version, "执行异常: " + truncate(String.valueOf(e.getMessage())));
                }
            } catch (Exception markError) {
                log.warn("索引版本失败回写异常, taskId={}", taskId, markError);
            }
            finishFailed(taskId, PipelineTaskErrorCode.INDEX_BUILD_FAILED.name(),
                    truncate(String.valueOf(e.getMessage())));
        }
    }

    /**
     * 评测冻结集回填（B8.1）：遍历范围文件 × 单一取数口径（selectComboProducts）→ 组装 → 幂等追加。
     * 缺口文件跳过（computeExpected 对账报缺、不进集合）；账本不逐条更，READY 时以对账期望统一收敛。
     */
    private void backfillFrozenScope(ComboSnapshot combo, String collectionName) {
        List<Long> scope = combo.getFileResultIds();
        if (ObjectUtil.isNull(scope) || scope.isEmpty()) {
            return;
        }
        Map<String, KbChunkSet> latestChunk = indexComboReconciler.latestChunkMap(scope);
        Map<String, KbEmbeddingSet> latestEmbed = indexComboReconciler.latestEmbedMap(scope);
        for (Long fileId : scope) {
            IndexComboReconciler.ComboProducts products = indexComboReconciler
                    .selectComboProducts(combo, fileId, latestChunk, latestEmbed);
            if (!products.complete()) {
                log.info("===> IndexBuildTaskRunner 冻结集回填跳过（缺口，对账将报缺）, fileResultId={}, gap={}",
                        fileId, products.gap());
                continue;
            }
            KbFileResult file = fileResultDbService.getById(fileId);
            List<IndexRow> rows = indexRowAssembler.assemble(fileId,
                    ObjectUtil.isNull(file) ? "" : StrUtil.blankToDefault(file.getOwner(), ""),
                    products.chunkRow(), products.embedRow());
            if (ObjectUtil.isNull(rows) || rows.isEmpty()) {
                continue;
            }
            milvusIndexPort.append(collectionName, rows);
        }
        log.info("===> IndexBuildTaskRunner 冻结集回填完成, collection={}, scopeSize={}",
                collectionName, scope.size());
    }

    /** 发布判定：COMPENSATE 恒自动；INCREMENT 且绑定开启自动；NEW（首建）与 LIST 冻结集永不自动；其余停留 READY */
    private boolean shouldAutoPublish(Long kbId, ComboSnapshot combo, String trigger) {
        if (combo.isListScope() || IndexBuildTrigger.NEW.name().equals(trigger)) {
            return false;
        }
        if (IndexBuildTrigger.COMPENSATE.name().equals(trigger)) {
            return true;
        }
        if (IndexBuildTrigger.INCREMENT.name().equals(trigger)) {
            KnowledgeBase kb = knowledgeBaseDbService.getById(kbId);
            return ObjectUtil.isNull(kb) || !Integer.valueOf(0).equals(kb.getStrategyBindingEnabled());
        }
        return false;
    }

    private void markFailed(KbIndexVersion version, String buildError) {
        version.setStatus(IndexVersionStatus.FAILED.name());
        version.setBuildError(truncate(buildError, 1024));
        indexVersionDbService.updateById(version);
    }

    private void finishFailed(Long taskId, String errorCode, String errorMsg) {
        pipelineTaskDbService.finish(taskId, PipelineTaskStatus.FAILED.name(), errorCode,
                StrUtil.isBlank(errorMsg) ? null : truncate(errorMsg, 1000));
    }

    private String truncate(String message) {
        return StrUtil.maxLength(message, 1000);
    }

    private String truncate(String message, int maxLength) {
        return StrUtil.maxLength(message, maxLength);
    }
}
