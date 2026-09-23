package com.knowledge.biz.service.support;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.knowledge.biz.service.db.KbPipelineProductDbService;
import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.common.utils.JsonUtil;
import com.knowledge.worker.preprocessing.strategy.PreprocessStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 索引血缘解析（step-13 B1，2026-09 定稿）：
 * 文件产物的"预处理策略"沿 upstream 链读取——切片产物行的上游预处理产物 capabilitySnapshot
 * 存有完整 PreprocessStrategy JSON（PreprocessTaskRunner 落库口径），解析出 name-version。
 * 组合映射按环节可扩展：新增环节策略时，按同模式从对应产物的 capabilitySnapshot 读取，
 * ComboSnapshot 结构与索引构建逻辑零改动。
 *
 * @author cxxl
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexLineageResolver {

    private final KbPipelineProductDbService productDbService;

    /**
     * 由切片产物的上游预处理产物 ID 解析预处理策略 name-version。
     * 产物缺失/快照为空/解析失败 → null（调用方按"血统不完整"降级，不参与组合枚举与构建）。
     */
    public String resolvePreprocessStrategy(Long upstreamProductId) {
        if (ObjectUtil.isNull(upstreamProductId)) {
            return null;
        }
        KbPipelineProduct product = productDbService.getById(upstreamProductId);
        if (ObjectUtil.isNull(product) || StrUtil.isBlank(product.getCapabilitySnapshot())) {
            log.warn("===> IndexLineageResolver 预处理产物缺失或快照为空, upstreamProductId={}", upstreamProductId);
            return null;
        }
        try {
            PreprocessStrategy strategy = JsonUtil.toObject(product.getCapabilitySnapshot(), PreprocessStrategy.class);
            if (ObjectUtil.isNull(strategy) || StrUtil.isBlank(strategy.getName())) {
                log.warn("===> IndexLineageResolver 预处理策略快照结构缺失, upstreamProductId={}", upstreamProductId);
                return null;
            }
            return strategy.fullVersion();
        } catch (Exception e) {
            log.warn("===> IndexLineageResolver 预处理策略快照解析失败, upstreamProductId={}", upstreamProductId, e);
            return null;
        }
    }
}
