package com.knowledge.filecenter.db;

import com.knowledge.common.domain.entity.KbFileObject;
import com.knowledge.infra.persistence.InfraDbService;

/**
 * 文件档案数据访问。
 *
 * @author cxxl
 */
public interface KbFileObjectDbService extends InfraDbService<KbFileObject> {

    /** 按文件 ID 查档案 */
    KbFileObject getByFileId(String fileId);
}
