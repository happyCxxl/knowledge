package com.knowledge.model.catalog;

import com.knowledge.common.enums.embed.EmbeddingModel;

import java.util.List;

/**
 * 模型目录 Port：按模型名取目录项（维度/度量/归一化/窗口/批上限/启用）。
 * 一期实现 = 静态注册 {@link StaticModelCatalog}（目录数据来自 EmbeddingModel 枚举）；
 * 供应商目录接口就绪后新增远程实现替换注入即可，调用方零改动。
 *
 * @author cxxl
 */
public interface ModelCatalogPort {

    /** 按模型名取目录项；未识别返回 null（调用方兜底） */
    EmbeddingModel get(String modelKey);

    /** 启用中模型列表（前端下拉/默认模型选择） */
    List<EmbeddingModel> listEnabled();
}
