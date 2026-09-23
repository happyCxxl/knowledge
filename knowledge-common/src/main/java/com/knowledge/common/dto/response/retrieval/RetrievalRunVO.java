package com.knowledge.common.dto.response.retrieval;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 检索运行记录（step-14 B6，B10）：四元组 + 耗时 + 时间戳（结果快照按需经对比回放接口展开）。
 *
 * @author cxxl
 */
@Data
public class RetrievalRunVO {

    /** 运行记录 ID */
    private Long id;

    /** 索引版本行 ID */
    private Long versionId;

    /** 版本号 */
    private String versionNo;

    /** 规则行 ID */
    private Long ruleId;

    /** 规则 name-version */
    private String ruleNameVersion;

    /** 查询文本 */
    private String query;

    /** 耗时（毫秒） */
    private Integer elapsedMs;

    /** 执行时间 */
    private LocalDateTime createTime;
}
