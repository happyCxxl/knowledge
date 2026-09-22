package com.knowledge.biz.service;

import com.knowledge.common.dto.response.structure.StructureDetailVO;
import com.knowledge.common.dto.response.task.StageTriggerVO;

/**
 * 结构组装控制面服务（组装环节）：触发组装（手动逐环节，重跑同入口）+ 组装详情。
 *
 * @author cxxl
 */
public interface StructureControlService {

    /**
     * 触发组装：上游解析产物校验 → 防重/唤醒（RUNNING 40431、QUEUED 补投唤醒）→
     * 新建 STRUCTURE 任务入队（不重新解析）；upstreamProductId 可选（指定上游解析产物，缺省取最新）；
     * 无解析产物时拒绝（40432）。
     */
    StageTriggerVO structure(Long fileResultId, Long upstreamProductId);

    /**
     * 组装详情：任务状态 + 子步骤 + 组装统计/冲突/章节/产物引用；
     * taskId 可选（缺省取最新任务，传了则查该次运行）。
     */
    StructureDetailVO structureDetail(Long fileResultId, Long taskId);
}
