package com.knowledge.filecenter.db.impl;

import com.knowledge.common.domain.entity.KbFileObject;
import com.knowledge.common.utils.NullUtil;
import com.knowledge.filecenter.db.KbFileObjectDbService;
import com.knowledge.filecenter.mapper.KbFileObjectMapper;
import com.knowledge.infra.persistence.InfraDbServiceImpl;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

/**
 * 文件档案数据访问实现。
 *
 * @author cxxl
 */
@Service
public class KbFileObjectDbServiceImpl extends InfraDbServiceImpl<KbFileObjectMapper, KbFileObject>
        implements KbFileObjectDbService {

    @Override
    public KbFileObject getByFileId(String fileId) {
        return this.lambdaQuery().eq(KbFileObject::getFileId, fileId).one();
    }

    @Override
    public List<KbFileObject> listByFileIds(Collection<String> fileIds) {
        if (NullUtil.isNull(fileIds) || fileIds.isEmpty()) {
            return List.of();
        }
        return this.lambdaQuery().in(KbFileObject::getFileId, fileIds).list();
    }
}
