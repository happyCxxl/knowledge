package com.knowledge.common.dto.response.parse;

import com.knowledge.common.dto.response.task.StageDetailVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 解析详情 VO：工作台"解析详情"页数据源。
 *
 * @author cxxl
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ParseDetailVO extends StageDetailVO {

    /** 质量告警（从产物 JSON 提取；无产物时为空列表） */
    private List<String> warnings;
}
