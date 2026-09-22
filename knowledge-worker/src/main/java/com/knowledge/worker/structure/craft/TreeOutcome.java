package com.knowledge.worker.structure.craft;

import com.knowledge.common.domain.structure.DocumentRelation;
import com.knowledge.common.domain.structure.UnifiedElement;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 结构组装产出：元素（类型/层级更新）+ 关系 + 推定统计（内部流转，不出 worker）。
 *
 * @author cxxl
 */
@Data
public class TreeOutcome {

    /** 组装后元素 */
    private List<UnifiedElement> elements = new ArrayList<>();

    /** 关系（PARENT_CHILD/TABLE_CELL_OF/NEXT/PREVIOUS…） */
    private List<DocumentRelation> relations = new ArrayList<>();

    /** 标题推定数 */
    private int titleCount;

    /** 标题推定分布（按级联层） */
    private Map<String, Integer> titleCountByCascade;

    /** 标题候选告警数 */
    private int titleCandidateCount;

    /** 无法挂树的元素 ID */
    private List<String> unattachableElements = new ArrayList<>();
}
