package com.knowledge.biz.service.db.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledge.biz.mapper.KbChunkMapper;
import com.knowledge.biz.service.db.KbChunkDbService;
import com.knowledge.common.domain.entity.KbChunk;
import com.knowledge.infra.persistence.InfraDbServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 切片数据访问服务实现。
 *
 * @author cxxl
 */
@Service
public class KbChunkDbServiceImpl extends InfraDbServiceImpl<KbChunkMapper, KbChunk>
        implements KbChunkDbService {

    @Override
    public List<KbChunk> listByChunkSetId(Long chunkSetId) {
        LambdaQueryWrapper<KbChunk> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(KbChunk::getChunkSetId, chunkSetId)
                .orderByAsc(KbChunk::getOrderNo)
                .orderByAsc(KbChunk::getId);
        return list(queryWrapper);
    }
}
