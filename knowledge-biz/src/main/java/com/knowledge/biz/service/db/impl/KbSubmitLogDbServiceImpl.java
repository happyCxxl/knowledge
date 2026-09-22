package com.knowledge.biz.service.db.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.knowledge.biz.mapper.KbSubmitLogMapper;
import com.knowledge.biz.service.db.KbSubmitLogDbService;
import com.knowledge.common.domain.entity.KbSubmitLog;
import com.knowledge.infra.persistence.InfraDbServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 提交日志数据访问服务实现。
 *
 * @author cxxl
 */
@Service
public class KbSubmitLogDbServiceImpl extends InfraDbServiceImpl<KbSubmitLogMapper, KbSubmitLog>
        implements KbSubmitLogDbService {

    @Override
    public KbSubmitLog getByRequestId(String requestId) {
        LambdaQueryWrapper<KbSubmitLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KbSubmitLog::getRequestId, requestId).last("LIMIT 1");
        return getOne(queryWrapper, false);
    }

    @Override
    public IPage<KbSubmitLog> pageByKb(long current, long size, Long knowledgeBaseId, String status, String fileName) {
        LambdaQueryWrapper<KbSubmitLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KbSubmitLog::getKnowledgeBaseId, knowledgeBaseId)
                .eq(StrUtil.isNotBlank(status), KbSubmitLog::getStatus, status)
                .like(StrUtil.isNotBlank(fileName), KbSubmitLog::getFileName, fileName)
                .orderByDesc(KbSubmitLog::getId);
        return page(new Page<>(current, size), queryWrapper);
    }
}
