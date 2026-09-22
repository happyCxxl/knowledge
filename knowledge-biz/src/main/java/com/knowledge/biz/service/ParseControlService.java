package com.knowledge.biz.service;

import com.knowledge.common.dto.response.parse.ParseDetailVO;
import com.knowledge.common.dto.response.task.StageTriggerVO;

/**
 * 解析控制面服务：触发解析（手动逐环节，首次解析与失败重跑同一入口）与解析详情。
 *
 * @author cxxl
 */
public interface ParseControlService {

    /**
     * 触发解析（首次解析与失败重跑同一入口，手动逐环节）：
     * QUEUED 任务直接入队唤醒、RUNNING 拒绝（40431）、成功/部分成功拒绝（40437）、
     * FAILED/CANCELLED/无任务新建 PARSE 任务并入队；旧任务/旧产物不动。完成后停在终态，不自动触发组装。
     */
    StageTriggerVO parse(Long fileResultId);

    /**
     * 解析详情：任务状态 + 子步骤列表 + 产物引用/告警。
     *
     * @param fileResultId 文件结果 ID
     * @param taskId       可选：指定某次运行的任务查看历史详情；为空取最新任务
     */
    ParseDetailVO parseDetail(Long fileResultId, Long taskId);
}
