package com.knowledge.worker.preprocessing.strategy;

import cn.hutool.core.util.StrUtil;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.worker.preprocessing.PreprocessProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 预处理策略解析器：快照 JSON → PreprocessStrategy（补全缺省规则/默认参数/custom 默认）。
 * 触发（runner 读任务快照）与保存侧（控制面 toStrategy）共用同一解析口径；解析失败回退内置默认（防御性兜底）。
 * 旧扁平格式（options 平铺）不再支持。
 *
 * @author cxxl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreprocessStrategyParser {

    private final PreprocessProperties properties;

    /** 内置默认策略（库内无启用版本时回退；name=preproc-default, version=v1） */
    public PreprocessStrategy defaultStrategy() {
        PreprocessStrategy strategy = new PreprocessStrategy();
        strategy.setName(PreprocessStrategy.BUILTIN_NAME);
        strategy.setVersion(PreprocessStrategy.BUILTIN_VERSION);
        return PreprocessAlgorithmSpec.normalize(strategy, properties);
    }

    /** 快照解析：空/失败回退内置默认；成功则补全默认后返回 */
    public PreprocessStrategy parse(String snapshot) {
        if (StrUtil.isBlank(snapshot)) {
            return defaultStrategy();
        }
        try {
            PreprocessStrategy strategy = JsonUtil.toObject(snapshot, PreprocessStrategy.class);
            if (strategy == null) {
                return defaultStrategy();
            }
            return PreprocessAlgorithmSpec.normalize(strategy, properties);
        } catch (Exception e) {
            log.warn("预处理策略快照解析失败，回退内置默认, snapshot={}", StrUtil.maxLength(snapshot, 200), e);
            return defaultStrategy();
        }
    }

    /** 配置 JSON（仅 rules/custom，无 name/version）→ 补全默认后的策略对象（name/version 由调用方设置） */
    public PreprocessStrategy parseConfig(String configSnapshot) {
        PreprocessStrategy strategy = parse(configSnapshot);
        strategy.setName(null);
        strategy.setVersion(null);
        return strategy;
    }
}
