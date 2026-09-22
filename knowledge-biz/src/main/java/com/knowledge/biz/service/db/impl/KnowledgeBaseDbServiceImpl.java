package com.knowledge.biz.service.db.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledge.biz.mapper.KnowledgeBaseMapper;
import com.knowledge.biz.service.db.KnowledgeBaseDbService;
import com.knowledge.common.domain.entity.KnowledgeBase;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.ThrowUtil;
import com.knowledge.infra.persistence.InfraDbServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 知识库数据访问服务实现。
 *
 * @author cxxl
 */
@Service
public class KnowledgeBaseDbServiceImpl extends InfraDbServiceImpl<KnowledgeBaseMapper, KnowledgeBase>
        implements KnowledgeBaseDbService {

    @Override
    public IPage<KnowledgeBase> pageByName(long current, long size, String name) {
        LambdaQueryWrapper<KnowledgeBase> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StrUtil.isNotBlank(name), KnowledgeBase::getName, name)
                .orderByDesc(KnowledgeBase::getId);
        return page(new Page<>(current, size), queryWrapper);
    }

    /**
     * 获取存在且未删除的知识库：getById 后判空，不存在/已删除抛 KB_NOT_FOUND（40401）。
     */
    @Override
    public KnowledgeBase getActiveById(Long id) {
        KnowledgeBase kb = getById(id);
        ThrowUtil.throwIf(ObjectUtil.isNull(kb), ErrorCode.KB_NOT_FOUND);
        return kb;
    }
}
