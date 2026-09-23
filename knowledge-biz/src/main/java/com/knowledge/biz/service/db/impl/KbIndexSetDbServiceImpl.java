package com.knowledge.biz.service.db.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.knowledge.biz.mapper.KbIndexSetMapper;
import com.knowledge.biz.service.db.KbIndexSetDbService;
import com.knowledge.common.domain.entity.KbIndexSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 索引集合数据访问服务实现（step-13 B08）。
 *
 * @author cxxl
 */
@Slf4j
@Service
public class KbIndexSetDbServiceImpl extends ServiceImpl<KbIndexSetMapper, KbIndexSet>
        implements KbIndexSetDbService {

    @Override
    public KbIndexSet getByKb(Long knowledgeBaseId) {
        LambdaQueryWrapper<KbIndexSet> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KbIndexSet::getKnowledgeBaseId, knowledgeBaseId)
                .last("LIMIT 1");
        return getOne(queryWrapper, false);
    }

    @Override
    public KbIndexSet getOrCreateByKb(Long knowledgeBaseId) {
        KbIndexSet existing = getByKb(knowledgeBaseId);
        if (existing != null) {
            return existing;
        }
        KbIndexSet created = new KbIndexSet();
        created.setKnowledgeBaseId(knowledgeBaseId);
        try {
            save(created);
            return created;
        } catch (DuplicateKeyException e) {
            // uk_kb 并发兜底：冲突后返回既有行
            log.info("===> KbIndexSetDbServiceImpl 集合行并发创建冲突, 返回既有行, kbId={}", knowledgeBaseId);
            return getByKb(knowledgeBaseId);
        }
    }

    @Override
    public void updatePublishedVersion(Long setId, Long versionId) {
        LambdaUpdateWrapper<KbIndexSet> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(KbIndexSet::getId, setId)
                .set(KbIndexSet::getCurrentPublishedVersionId, versionId);
        update(updateWrapper);
    }
}
