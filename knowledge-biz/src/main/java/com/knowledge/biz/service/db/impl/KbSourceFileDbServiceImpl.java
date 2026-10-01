package com.knowledge.biz.service.db.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.biz.mapper.KbSourceFileMapper;
import com.knowledge.biz.service.db.KbSourceFileDbService;
import com.knowledge.common.domain.entity.KbSourceFile;
import com.knowledge.infra.persistence.InfraDbServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * 来源文件数据访问服务实现。
 *
 * @author cxxl
 */
@Service
public class KbSourceFileDbServiceImpl extends InfraDbServiceImpl<KbSourceFileMapper, KbSourceFile>
        implements KbSourceFileDbService {

    @Override
    public KbSourceFile findByFileId(String fileId) {
        LambdaQueryWrapper<KbSourceFile> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KbSourceFile::getFileId, fileId).last("LIMIT 1");
        return getOne(queryWrapper, false);
    }

    @Override
    public List<KbSourceFile> listByFileIds(Collection<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<KbSourceFile> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(KbSourceFile::getFileId, fileIds);
        return list(queryWrapper);
    }
}
