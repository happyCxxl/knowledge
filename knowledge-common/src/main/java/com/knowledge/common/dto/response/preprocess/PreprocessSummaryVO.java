package com.knowledge.common.dto.response.preprocess;

import lombok.Data;

/**
 * 预处理统计 VO：PreprocessView 统计指标的对外视图（从派生视图推导的汇总指标，展示用）。
 *
 * @author cxxl
 */
@Data
public class PreprocessSummaryVO {

    /** 视图元素总数 */
    private Integer elementCount;

    /** 检索文本与展示文本不同的元素数 */
    private Integer changedCount;

    /** 剔除元素数（EXCLUDED_* 与 BACKUP_SKIPPED，不进内容流） */
    private Integer excludedCount;

    /** 重复元素数（REPEATED） */
    private Integer repeatedCount;

    /** 标准化字段总数（金额/日期/面积/证书号） */
    private Integer fieldCount;
}
