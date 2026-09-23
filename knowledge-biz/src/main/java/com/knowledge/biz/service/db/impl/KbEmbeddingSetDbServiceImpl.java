package com.knowledge.biz.service.db.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.biz.mapper.KbEmbeddingSetMapper;
import com.knowledge.biz.service.db.KbEmbeddingSetDbService;
import com.knowledge.common.domain.entity.KbEmbeddingSet;
import com.knowledge.common.enums.task.RowStatus;
import com.knowledge.infra.persistence.InfraDbServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 向量产物集合数据访问服务实现。
 *
 * @author cxxl
 */
@Service
public class KbEmbeddingSetDbServiceImpl extends InfraDbServiceImpl<KbEmbeddingSetMapper, KbEmbeddingSet>
        implements KbEmbeddingSetDbService {

    @Override
    public KbEmbeddingSet getLatestByFileResultId(Long fileResultId) {
        LambdaQueryWrapper<KbEmbeddingSet> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KbEmbeddingSet::getFileResultId, fileResultId)
                .orderByDesc(KbEmbeddingSet::getId)
                .last("LIMIT 1");
        return getOne(queryWrapper, false);
    }

    @Override
    public List<KbEmbeddingSet> listByFileResultId(Long fileResultId) {
        LambdaQueryWrapper<KbEmbeddingSet> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KbEmbeddingSet::getFileResultId, fileResultId)
                .orderByAsc(KbEmbeddingSet::getId);
        return list(queryWrapper);
    }

    @Override
    public List<KbEmbeddingSet> listHistoryByFileResultIdAndStrategyVersion(Long fileResultId,
                                                                            String strategyVersion, int limit) {
        LambdaQueryWrapper<KbEmbeddingSet> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KbEmbeddingSet::getFileResultId, fileResultId)
                .eq(KbEmbeddingSet::getStrategyVersion, strategyVersion)
                .eq(KbEmbeddingSet::getStatus, RowStatus.ACTIVE.name())
                .orderByDesc(KbEmbeddingSet::getId)
                .last("LIMIT " + Math.max(1, limit));
        return list(queryWrapper);
    }

    @Override
    public KbEmbeddingSet getByArtifactId(String artifactId) {
        LambdaQueryWrapper<KbEmbeddingSet> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KbEmbeddingSet::getArtifactId, artifactId)
                .orderByDesc(KbEmbeddingSet::getId)
                .last("LIMIT 1");
        return getOne(queryWrapper, false);
    }

    @Override
    public List<KbEmbeddingSet> listByFileResultIds(List<Long> fileResultIds) {
        if (fileResultIds == null || fileResultIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<KbEmbeddingSet> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(KbEmbeddingSet::getFileResultId, fileResultIds)
                .orderByAsc(KbEmbeddingSet::getId);
        return list(queryWrapper);
    }
}
