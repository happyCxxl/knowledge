package com.knowledge.biz.service.support;

import cn.hutool.core.util.ObjectUtil;
import com.knowledge.biz.service.db.KbPipelineTaskDbService;
import com.knowledge.common.domain.entity.KbPipelineTask;
import com.knowledge.common.enums.task.PipelineStage;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 环节详情任务解析共用助手：指定 taskId 校验归属与环节、缺省取该环节最新任务。
 *
 * @author cxxl
 */
@Component
@RequiredArgsConstructor
public class TaskDetailSupport {

    private final KbPipelineTaskDbService pipelineTaskDbService;

    /**
     * 解析详情任务：taskId 指定则查该次运行并校验归属与环节（不匹配 40001）；缺省取该环节最新任务（可为空）。
     *
     * @param stageLabel 环节中文名（错误提示用）
     */
    public KbPipelineTask resolveTask(Long fileResultId, PipelineStage stage, Long taskId, String stageLabel) {
        if (ObjectUtil.isNull(taskId)) {
            return pipelineTaskDbService.getByFileResultIdAndStage(fileResultId, stage.name());
        }
        KbPipelineTask task = pipelineTaskDbService.getById(taskId);
        if (ObjectUtil.isNull(task)
                || !fileResultId.equals(task.getFileResultId())
                || !stage.name().equals(task.getStage())) {
            throw new KnowledgeException(ErrorCode.PARAM_INVALID, "任务不存在或不属于该文件的" + stageLabel + "任务");
        }
        return task;
    }
}
