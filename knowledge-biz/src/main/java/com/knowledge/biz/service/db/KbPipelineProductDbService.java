package com.knowledge.biz.service.db;

import com.knowledge.common.domain.entity.KbPipelineProduct;
import com.knowledge.infra.persistence.InfraDbService;

import java.util.List;

/**
 * 阶段产物数据访问服务（kb_pipeline_product）。
 *
 * @author cxxl
 */
public interface KbPipelineProductDbService extends InfraDbService<KbPipelineProduct> {

    /**
     * 按文件结果 + 环节查最新产物（id 倒序取一条）。
     *
     * @param fileResultId 文件结果 ID
     * @param stage        环节（PipelineStage 枚举名）
     * @return 产物实体；不存在返回 null
     */
    KbPipelineProduct getByFileResultIdAndStage(Long fileResultId, String stage);

    /**
     * 查单文件全部产物（id 升序）。
     *
     * @param fileResultId 文件结果 ID
     * @return 产物列表
     */
    List<KbPipelineProduct> listByFileResultId(Long fileResultId);
}
