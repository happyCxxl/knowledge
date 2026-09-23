package com.knowledge.worker.indexing;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.common.enums.index.IndexShape;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组合快照（step-13 B08，2026-09 定稿：策略集合模型）：
 * 组合身份 = 文件范围 × 索引形态 × 环节策略映射（stage → name-version，环节可扩展）。
 * 记入 kb_index_version.combo_snapshot（JSON）；组合全等判定 = 三部分全等（JSON 序列化确定性：
 * stageStrategies 为 LinkedHashMap 保持插入序）。
 * 集合名 = kb_{kbId}_{versionNo}（versionNo = 组合注册序号），同组合复用同集合。
 * <p>
 * 格式断言：stageStrategies 三环节维度是组合的身份与血缘载体，{@link #requireStageStrategies()}
 * 是旧口径快照（重构前无该维度）的唯一拒绝点——业务路径按需显式断言，不再散点防御；
 * 候选构建"待绑定补齐"场景用非抛断言 {@link #hasCompleteStageStrategies()} 判断后再补齐。
 *
 * @author cxxl
 */
@Data
public class ComboSnapshot implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 文件范围模式：ALL（全部）/ LIST（评测冻结集，B8.1 已激活） */
    private String fileScopeMode = "ALL";

    /** LIST 模式下的文件结果 ID 列表（ALL 模式为 null） */
    private List<Long> fileResultIds;

    /** 索引形态（一期 FULL_VECTOR） */
    private IndexShape shape = IndexShape.FULL_VECTOR;

    /** 环节策略映射：stage（PipelineStage 名）→ 策略 name-version（环节白名单：PREPROCESS/CHUNK/EMBED，构建入口校验） */
    private Map<String, String> stageStrategies = new LinkedHashMap<>();

    /** 取某环节策略 name-version（无 → null） */
    public String strategyOf(String stage) {
        return stageStrategies.get(stage);
    }

    /** 预处理策略 name-version */
    public String getPreprocessStrategy() {
        return strategyOf(PipelineStage.PREPROCESS.name());
    }

    /** 切片策略 name-version */
    public String getChunkStrategy() {
        return strategyOf(PipelineStage.CHUNK.name());
    }

    /** 向量策略 name-version */
    public String getEmbedStrategy() {
        return strategyOf(PipelineStage.EMBED.name());
    }

    /** 格式断言（非抛）：预处理/切片/向量三环节策略维度齐全且非空 */
    public boolean hasCompleteStageStrategies() {
        return ObjectUtil.isNotNull(stageStrategies)
                && StrUtil.isNotBlank(strategyOf(PipelineStage.PREPROCESS.name()))
                && StrUtil.isNotBlank(strategyOf(PipelineStage.CHUNK.name()))
                && StrUtil.isNotBlank(strategyOf(PipelineStage.EMBED.name()));
    }

    /** 格式断言（抛）：三环节策略维度不完整 → 40448（旧口径数据需废弃重灌，不静默降级） */
    public void requireStageStrategies() {
        if (!hasCompleteStageStrategies()) {
            throw new KnowledgeException(ErrorCode.INDEX_COMBO_SNAPSHOT_LEGACY);
        }
    }

    /** 是否评测冻结集范围（LIST；B8.1 激活，见实现文档 §12） */
    public boolean isListScope() {
        return "LIST".equals(fileScopeMode);
    }

    /** 一期三环节组合工厂（保持调用点简洁；插入序 = 预处理/切片/向量化） */
    public static ComboSnapshot of(String preprocess, String chunk, String embed) {
        ComboSnapshot combo = new ComboSnapshot();
        combo.getStageStrategies().put(PipelineStage.PREPROCESS.name(), preprocess);
        combo.getStageStrategies().put(PipelineStage.CHUNK.name(), chunk);
        combo.getStageStrategies().put(PipelineStage.EMBED.name(), embed);
        return combo;
    }
}
