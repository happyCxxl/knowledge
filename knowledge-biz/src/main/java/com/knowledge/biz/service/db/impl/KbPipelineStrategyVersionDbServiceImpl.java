package com.knowledge.biz.service.db.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.biz.mapper.KbPipelineStrategyVersionMapper;
import com.knowledge.biz.service.db.KbPipelineStrategyVersionDbService;
import com.knowledge.common.domain.entity.KbPipelineStrategyVersion;
import com.knowledge.common.enums.task.RowStatus;
import com.knowledge.infra.persistence.InfraDbServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 环节策略版本数据访问服务实现。
 *
 * @author cxxl
 */
@Service
public class KbPipelineStrategyVersionDbServiceImpl
        extends InfraDbServiceImpl<KbPipelineStrategyVersionMapper, KbPipelineStrategyVersion>
        implements KbPipelineStrategyVersionDbService {

    @Override
    public KbPipelineStrategyVersion getLatestEnabledByType(String type) {
        LambdaQueryWrapper<KbPipelineStrategyVersion> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KbPipelineStrategyVersion::getType, type)
                .eq(KbPipelineStrategyVersion::getStatus, RowStatus.ACTIVE.name())
                .orderByDesc(KbPipelineStrategyVersion::getId)
                .last("LIMIT 1");
        return getOne(queryWrapper, false);
    }

    @Override
    public KbPipelineStrategyVersion getEnabledByTypeAndVersion(String type, String version) {
        LambdaQueryWrapper<KbPipelineStrategyVersion> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KbPipelineStrategyVersion::getType, type)
                .eq(KbPipelineStrategyVersion::getVersion, version)
                .eq(KbPipelineStrategyVersion::getStatus, RowStatus.ACTIVE.name())
                .orderByDesc(KbPipelineStrategyVersion::getId)
                .last("LIMIT 1");
        return getOne(queryWrapper, false);
    }
}
