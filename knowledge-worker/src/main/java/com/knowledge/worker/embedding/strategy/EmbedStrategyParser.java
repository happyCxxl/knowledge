package com.knowledge.worker.embedding.strategy;

import cn.hutool.core.util.StrUtil;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.model.catalog.ModelCatalogPort;
import com.knowledge.worker.embedding.EmbedProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 向量化策略解析器：快照 JSON → EmbedStrategy（补默认 + 目录冗余回填）。
 * 触发（runner 读任务快照）与保存侧（控制面 toStrategy）共用同一解析口径；解析失败回退内置默认（防御性兜底）。
 *
 * @author cxxl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbedStrategyParser {

    private final EmbedProperties properties;

    private final ModelCatalogPort catalog;

    /** 内置默认策略（库内无启用版本时回退；name=embed-default, version=v1，模型=首个启用模型） */
    public EmbedStrategy defaultStrategy() {
        EmbedStrategy strategy = new EmbedStrategy();
        strategy.setName(EmbedStrategy.BUILTIN_NAME);
        strategy.setVersion(EmbedStrategy.BUILTIN_VERSION);
        return EmbedAlgorithmSpec.normalize(strategy, properties, catalog);
    }

    /** 快照解析：空/失败回退内置默认；成功则补全默认后返回 */
    public EmbedStrategy parse(String snapshot) {
        if (StrUtil.isBlank(snapshot)) {
            return defaultStrategy();
        }
        try {
            EmbedStrategy strategy = JsonUtil.toObject(snapshot, EmbedStrategy.class);
            if (strategy == null) {
                return defaultStrategy();
            }
            return EmbedAlgorithmSpec.normalize(strategy, properties, catalog);
        } catch (Exception e) {
            log.warn("向量化策略快照解析失败，回退内置默认, snapshot={}", StrUtil.maxLength(snapshot, 200), e);
            return defaultStrategy();
        }
    }

    /** 配置 JSON（无 name/version）→ 补全默认后的策略对象（name/version 由调用方设置） */
    public EmbedStrategy parseConfig(String configSnapshot) {
        EmbedStrategy strategy = parse(configSnapshot);
        strategy.setName(null);
        strategy.setVersion(null);
        return strategy;
    }
}
