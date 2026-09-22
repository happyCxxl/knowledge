package com.knowledge.worker.structure.craft;

import com.knowledge.common.domain.structure.UnifiedElement;
import com.knowledge.worker.structure.AssembleContext;

import java.util.List;

/**
 * 结构组装：标题三级级联（样式→编号模式→字号/加粗）+ 章节树 PARENT_CHILD +
 * 表格归属（TABLE_CELL_OF；CAPTION_OF 预留未实现）+ Excel sheet→SECTION。
 *
 * @author cxxl
 */
public interface StructureAssembler {

    /**
     * 组装结构。
     *
     * @param ordered 阅读顺序后的元素
     * @param context 组装上下文
     * @return 树产出（元素/关系/推定统计）
     */
    TreeOutcome assembleTree(List<UnifiedElement> ordered, AssembleContext context);
}
