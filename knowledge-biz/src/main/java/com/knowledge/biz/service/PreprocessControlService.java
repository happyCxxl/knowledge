package com.knowledge.biz.service;

import com.knowledge.common.dto.response.preprocess.PreprocessDetailVO;
import com.knowledge.common.dto.response.preprocess.PreprocessTriggerVO;

/**
 * 预处理控制面：触发预处理/重跑（手动逐环节）+ 预处理详情。
 *
 * @author cxxl
 */
public interface PreprocessControlService {

    /**
     * 触发预处理（strategyVersionId 可选：策略行 ID，缺省取库内启用中最新版本，库内无回退内置默认）。
     *
     * @param fileResultId       文件结果 ID
     * @param strategyVersionId  策略版本行 ID（可空）
     * @param upstreamProductId 上游产物 ID（可选，指定 STRUCTURE 产物，缺省取最新）
     * @return 任务 ID + 生效策略版本
     */
    PreprocessTriggerVO preprocess(Long fileResultId, Long strategyVersionId, Long upstreamProductId);

    /**
     * 预处理详情：任务状态 + 子步骤 + 策略信息 + 预处理统计/视图元素/产物引用；
     * taskId 可选（缺省取最新任务，传了则查该次运行）。
     */
    PreprocessDetailVO preprocessDetail(Long fileResultId, Long taskId);
}
