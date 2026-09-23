package com.knowledge.common.dto.response.retrieval;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 检索响应（step-14 B5，B09）：命中列表 + 执行口径（规则/版本/耗时/运行记录 ID）。
 *
 * @author cxxl
 */
@Data
public class SearchVO {

    /** 查询文本 */
    private String query;

    /** 规则 name-version（引擎基线为 baseline-v0） */
    private String ruleNameVersion;

    /** 索引版本号 */
    private String versionNo;

    /** 耗时（毫秒） */
    private Long elapsedMs;

    /** 运行记录 ID（测试台检索有值；生产检索按开关记录） */
    private Long runId;

    /** 命中列表 */
    private List<SearchHitVO> hits = new ArrayList<>();
}
