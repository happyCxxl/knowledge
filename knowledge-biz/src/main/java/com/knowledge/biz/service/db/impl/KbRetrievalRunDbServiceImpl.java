package com.knowledge.biz.service.db.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.biz.mapper.KbRetrievalRunMapper;
import com.knowledge.biz.service.db.KbRetrievalRunDbService;
import com.knowledge.common.domain.entity.KbRetrievalRun;
import com.knowledge.infra.persistence.InfraDbServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * 检索运行记录数据访问服务实现（step-14 B6）。
 *
 * @author cxxl
 */
@Service
public class KbRetrievalRunDbServiceImpl
        extends InfraDbServiceImpl<KbRetrievalRunMapper, KbRetrievalRun>
        implements KbRetrievalRunDbService {

    @Override
    public List<KbRetrievalRun> listByKb(Long knowledgeBaseId, int limit) {
        LambdaQueryWrapper<KbRetrievalRun> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KbRetrievalRun::getKbId, knowledgeBaseId)
                .orderByDesc(KbRetrievalRun::getId);
        if (limit > 0) {
            queryWrapper.last("LIMIT " + limit);
        }
        return list(queryWrapper);
    }

    @Override
    public List<KbRetrievalRun> listByIdsOrdered(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<KbRetrievalRun> rows = listByIds(ids);
        return ids.stream()
                .map(id -> rows.stream().filter(r -> id.equals(r.getId())).findFirst().orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
