package com.knowledge.worker.parser.capability;

import com.knowledge.common.domain.parse.capability.LayoutResult;
import com.knowledge.common.domain.parse.capability.PageImage;
import com.knowledge.common.domain.parse.capability.ProviderContext;

/**
 * 版面分析能力接口（预留扩展，一期无实现）。
 * 值对象见 common.domain.parse（LayoutResult/PageImage/LayoutRegion）。
 *
 * @author cxxl
 */
public interface LayoutProvider {

    /**
     * 分析页面版面。
     *
     * @param page 页面图像
     * @param ctx  调用上下文
     * @return 版面结果（区域分类 + 阅读顺序）
     */
    LayoutResult analyze(PageImage page, ProviderContext ctx);
}
