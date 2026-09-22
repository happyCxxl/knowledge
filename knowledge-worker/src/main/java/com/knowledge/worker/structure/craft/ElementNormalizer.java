package com.knowledge.worker.structure.craft;

import com.knowledge.common.domain.parse.ParseSource;
import com.knowledge.common.domain.structure.UnifiedElement;
import com.knowledge.worker.structure.AssembleContext;

import java.util.List;

/**
 * 元素标准化：各路元素 → 统一元素雏形 + 坐标统一（左上角原点 + pt）。
 *
 * @author cxxl
 */
public interface ElementNormalizer {

    /**
     * 标准化。
     *
     * @param sources 多路解析结果（一期 native 单路）
     * @param context 组装上下文
     * @return 统一元素雏形列表（保留来源标记于 extension）
     */
    List<UnifiedElement> normalize(List<ParseSource> sources, AssembleContext context);
}
