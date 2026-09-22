package com.knowledge.common.domain.structure;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 组装报告：多工艺统计 + 溯源可回溯占比（落子步骤记录，工作台"组装详情"数据源）。
 *
 * @author cxxl
 */
@Data
public class AssembleReport {

    /** 标准化元素数 */
    private int normalizedCount;

    /** 去重合并对数 */
    private int mergePairs;

    /** 冲突数 */
    private int conflictCount;

    /** 阅读顺序切分数（预留未启用，从未赋值） */
    private int orderCuts;

    /** 标题推定数 */
    private int titleCount;

    /** 标题推定数（按级联层统计，如 style:1, number-pattern:3） */
    private java.util.Map<String, Integer> titleCountByCascade;

    /** 标题候选告警数 */
    private int titleCandidateCount;

    /** 跨页接续表数 */
    private int continuationCount;

    /** 溯源可回溯占比（0~1） */
    private double traceableRatio;

    /** 重复页数（组装环节识别，除首份外） */
    private int repeatPageCount;

    /** 重复段数（组装环节识别，除首份外） */
    private int repeatSegmentCount;

    /** 噪声页数（组装环节识别） */
    private int noisePageCount;

    /** 无法挂树的元素 ID 列表（组装完整性判定依据） */
    private List<String> unattachableElements = new ArrayList<>();
}
