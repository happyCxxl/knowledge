package com.knowledge.biz.service.db;

import com.knowledge.common.domain.entity.KbPipelineStrategyVersion;
import com.knowledge.infra.persistence.InfraDbService;

/**
 * 环节策略版本数据访问服务（只读；管理接口随策略管理阶段落地）。
 *
 * @author cxxl
 */
public interface KbPipelineStrategyVersionDbService extends InfraDbService<KbPipelineStrategyVersion> {

    /**
     * 取类型下启用中的最新版本（无 → null）。
     */
    KbPipelineStrategyVersion getLatestEnabledByType(String type);

    /**
     * 按类型 + 版本号取启用版本（无 → null）。
     */
    KbPipelineStrategyVersion getEnabledByTypeAndVersion(String type, String version);
}
