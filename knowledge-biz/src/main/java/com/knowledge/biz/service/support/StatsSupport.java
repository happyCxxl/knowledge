package com.knowledge.biz.service.support;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.common.dto.response.lineage.LineageNodeVO;
import com.knowledge.common.enums.task.PipelineTaskStatus;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.filecenter.service.FileStorage;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 环节统计公共口径：读产物本体、耗时、结论文案的前置判断，各环节的统计支持类共用。
 *
 * <p>纯读取，不落库、不改产物；各环节自己的口径（元素归类、异常项陈述）留在各自的类里。
 *
 * @author cxxl
 */
public final class StatsSupport {

    private StatsSupport() {
    }

    /**
     * 读产物本体；产物引用为空、对象读不到或 JSON 解析失败都返回 null。
     *
     * @param fileStorage 对象存储
     * @param artifactId  产物引用（sha256），可空
     * @param type        产物本体类型
     * @return 产物本体；取不到返回 null
     */
    public static <T> T readArtifact(FileStorage fileStorage, String artifactId, Class<T> type) {
        if (fileStorage == null || StrUtil.isBlank(artifactId)) {
            return null;
        }
        try {
            byte[] content = fileStorage.getObject(artifactId);
            if (ObjectUtil.isNull(content)) {
                return null;
            }
            return JsonUtil.toObject(new String(content, StandardCharsets.UTF_8), type);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 结论文案的前置判断：失败与已取消取失败原因，非成功 / 部分成功不陈述结论。
     *
     * @param errorMsg 失败原因
     * @param status   任务状态
     * @param failText 失败原因缺失时的兜底文案（按环节给）
     * @param body     统计陈述：统计缺失时由它返回 null
     * @return 结论文案；无可陈述内容时返回 null
     */
    public static String summaryOf(String errorMsg, String status, String failText, Supplier<String> body) {
        if (PipelineTaskStatus.FAILED.name().equals(status)
                || PipelineTaskStatus.CANCELLED.name().equals(status)) {
            return StrUtil.blankToDefault(errorMsg, failText);
        }
        if (!PipelineTaskStatus.SUCCESS.name().equals(status)
                && !PipelineTaskStatus.PARTIAL_SUCCESS.name().equals(status)) {
            return null;
        }
        return body.get();
    }

    /** 本次运行耗时：起止时间齐全时相减，缺失返回 null */
    public static Long durationMs(LocalDateTime startedAt, LocalDateTime finishedAt) {
        if (ObjectUtil.isNull(startedAt) || ObjectUtil.isNull(finishedAt)) {
            return null;
        }
        return Duration.between(startedAt, finishedAt).toMillis();
    }

    /**
     * 环节统计与摘要并入执行树节点：摘要写 {@code stageSummary}，统计并入通用 {@code stats}。
     *
     * <p>通用 stats 里可能已有该环节由库表聚合出来的键（切片 chunkCount、预处理 matched/changed），
     * 这里是"并入"而不是整份替换 —— 替换会把先落的通用键覆盖掉。
     *
     * @param node    执行树节点
     * @param stats   本环节统计（**可空**：产物不可读时不并入）
     * @param summary 本环节摘要（可空）
     */
    public static void mergeNodeStats(LineageNodeVO node, Map<String, Object> stats, String summary) {
        node.setStageSummary(summary);
        if (ObjectUtil.isNull(stats)) {
            return;
        }
        Map<String, Object> merged = ObjectUtil.isNull(node.getStats())
                ? new LinkedHashMap<>() : new LinkedHashMap<>(node.getStats());
        merged.putAll(stats);
        node.setStats(merged);
    }
}
