package com.knowledge.model.catalog;

import com.knowledge.common.enums.embed.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 静态模型目录（一期）：目录数据 = {@link EmbeddingModel} 枚举（common 单一事实源）。
 * 模型增删/参数修正只改枚举常量，本组件零改动。
 *
 * @author cxxl
 */
@Component
public class StaticModelCatalog implements ModelCatalogPort {

    @Override
    public EmbeddingModel get(String modelKey) {
        return EmbeddingModel.of(modelKey);
    }

    @Override
    public List<EmbeddingModel> listEnabled() {
        return EmbeddingModel.enabledModels();
    }
}
