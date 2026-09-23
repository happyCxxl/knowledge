package com.knowledge.biz.service.db.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.biz.mapper.KbChunkSetMapper;
import com.knowledge.biz.service.db.KbChunkSetDbService;
import com.knowledge.common.domain.entity.KbChunkSet;
import com.knowledge.infra.persistence.InfraDbServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 切片产物集合数据访问服务实现。
 *
 * @author cxxl
 */
@Service
public class KbChunkSetDbServiceImpl extends InfraDbServiceImpl<KbChunkSetMapper, KbChunkSet>
        implements KbChunkSetDbService {

    @Override
    public KbChunkSet getByArtifactId(String artifactId) {
        LambdaQueryWrapper<KbChunkSet> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KbChunkSet::getArtifactId, artifactId)
                .orderByDesc(KbChunkSet::getId)
                .last("LIMIT 1");
        return getOne(queryWrapper, false);
    }
}
