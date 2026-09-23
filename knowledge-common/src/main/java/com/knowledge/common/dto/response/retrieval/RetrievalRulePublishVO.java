package com.knowledge.common.dto.response.retrieval;

import lombok.Data;

/**
 * 检索规则选优发布响应（step-14 B2）。
 *
 * @author cxxl
 */
@Data
public class RetrievalRulePublishVO {

    /** 索引版本行 ID */
    private Long versionId;

    /** 版本号 */
    private String versionNo;

    /** 规则行 ID */
    private Long ruleId;

    /** 规则 name-version（快照冗余，展示用） */
    private String ruleNameVersion;
}
