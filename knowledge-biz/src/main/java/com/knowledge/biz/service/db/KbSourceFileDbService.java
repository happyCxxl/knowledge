package com.knowledge.biz.service.db;

import com.knowledge.common.domain.entity.KbSourceFile;
import com.knowledge.infra.persistence.InfraDbService;

/**
 * 来源文件数据访问服务（kb_source_file）。
 *
 * @author cxxl
 */
public interface KbSourceFileDbService extends InfraDbService<KbSourceFile> {

    /**
     * 按文件 ID 查来源文件。
     *
     * @param fileId 文件 ID（uk_file_id 唯一）
     * @return 来源文件实体；不存在返回 null
     */
    KbSourceFile findByFileId(String fileId);
}
