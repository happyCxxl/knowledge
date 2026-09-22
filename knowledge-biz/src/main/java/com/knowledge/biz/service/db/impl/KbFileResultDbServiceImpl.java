package com.knowledge.biz.service.db.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledge.biz.mapper.KbFileResultMapper;
import com.knowledge.biz.service.db.KbFileResultDbService;
import com.knowledge.common.domain.entity.KbFileResult;
import com.knowledge.infra.persistence.InfraDbServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 文件结果数据访问服务实现。
 *
 * @author cxxl
 */
@Service
public class KbFileResultDbServiceImpl extends InfraDbServiceImpl<KbFileResultMapper, KbFileResult>
        implements KbFileResultDbService {

    @Override
    public IPage<KbFileResult> pageByKb(long current, long size, Long knowledgeBaseId) {
        LambdaQueryWrapper<KbFileResult> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KbFileResult::getKnowledgeBaseId, knowledgeBaseId)
                .orderByDesc(KbFileResult::getId);
        return page(new Page<>(current, size), queryWrapper);
    }
}
