package com.knowledge.worker.chunking.strategy;

import cn.hutool.core.util.StrUtil;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.worker.chunking.ChunkProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 切片策略解析器：快照 JSON → ChunkStrategy（补全缺省路由/默认参数/流程层默认）。
 * 触发（runner 读任务快照）与保存侧（控制面 toStrategy）共用同一解析口径；解析失败回退内置默认（防御性兜底）。
 * 旧扁平格式（options 单键）不再支持。
 *
 * @author cxxl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChunkStrategyParser {

    private final ChunkProperties properties;

    /** 内置默认策略（库内无启用版本时回退；name=chunk-hybrid, version=v1） */
    public ChunkStrategy defaultStrategy() {
        ChunkStrategy strategy = new ChunkStrategy();
        strategy.setName(ChunkStrategy.BUILTIN_NAME);
        strategy.setVersion(ChunkStrategy.BUILTIN_VERSION);
        return ChunkAlgorithmSpec.normalize(strategy, properties);
    }

    /** 快照解析：空/失败回退内置默认；成功则补全默认后返回 */
    public ChunkStrategy parse(String snapshot) {
        if (StrUtil.isBlank(snapshot)) {
            return defaultStrategy();
        }
        try {
            ChunkStrategy strategy = JsonUtil.toObject(snapshot, ChunkStrategy.class);
            if (strategy == null) {
                return defaultStrategy();
            }
            return ChunkAlgorithmSpec.normalize(strategy, properties);
        } catch (Exception e) {
            log.warn("切片策略快照解析失败，回退内置默认, snapshot={}", StrUtil.maxLength(snapshot, 200), e);
            return defaultStrategy();
        }
    }

    /** 配置 JSON（仅 routes/pipeline，无 name/version）→ 补全默认后的策略对象（name/version 由调用方设置） */
    public ChunkStrategy parseConfig(String configSnapshot) {
        ChunkStrategy strategy = parse(configSnapshot);
        strategy.setName(null);
        strategy.setVersion(null);
        return strategy;
    }
}
