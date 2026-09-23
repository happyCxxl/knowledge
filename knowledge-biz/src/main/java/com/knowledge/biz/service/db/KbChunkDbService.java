package com.knowledge.biz.service.db;

import com.knowledge.common.domain.entity.KbChunk;
import com.knowledge.infra.persistence.InfraDbService;

import java.util.List;

/**
 * 切片数据访问服务（批量插入由 InfraDbService.saveBatch(list, size) 承担，≤500/批）。
 *
 * @author cxxl
 */
public interface KbChunkDbService extends InfraDbService<KbChunk> {

    /**
     * 按切片集合取全部切片（集合内顺序号升序；切片详情数据源）。
     */
    List<KbChunk> listByChunkSetId(Long chunkSetId);
}
