package com.knowledge.biz.controller;

import com.knowledge.biz.service.PreprocessControlService;
import com.knowledge.common.core.util.R;
import com.knowledge.common.dto.response.preprocess.PreprocessDetailVO;
import com.knowledge.common.dto.response.preprocess.PreprocessTriggerVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预处理控制面 Controller（手动逐环节触发点）。
 *
 * @author cxxl
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/file-results")
@Tag(name = "预处理控制", description = "触发预处理与重跑")
public class KnowledgeFilePreprocessController {

    private final PreprocessControlService preprocessControlService;

    @PostMapping("/{fileResultId}/preprocess")
    @Operation(summary = "触发预处理", description = "触发预处理/重跑（手动逐环节）；"
            + "strategyVersionId 可选（策略行 ID，缺省取库内启用中最新版本）；upstreamProductId 可选（指定上游 STRUCTURE 产物，缺省取最新）；RUNNING 拒绝（40431）、QUEUED 补投唤醒、终态/无任务新建")
    public R<PreprocessTriggerVO> preprocess(
            @Parameter(description = "文件结果ID", required = true) @PathVariable("fileResultId") Long fileResultId,
            @Parameter(description = "策略版本行 ID（可选）")
            @RequestParam(value = "strategyVersionId", required = false) Long strategyVersionId,
            @Parameter(description = "上游产物ID（可选，指定 STRUCTURE 产物）")
            @RequestParam(value = "upstreamProductId", required = false) Long upstreamProductId) {
        log.info("===> KnowledgeFilePreprocessController preprocess 触发预处理, fileResultId={}, strategyVersionId={}, upstreamProductId={}",
                fileResultId, strategyVersionId, upstreamProductId);
        return R.ok(preprocessControlService.preprocess(fileResultId, strategyVersionId, upstreamProductId));
    }

    @GetMapping("/{fileResultId}/preprocess-detail")
    @Operation(summary = "预处理详情", description = "任务状态 + 子步骤列表 + 策略信息 + 预处理统计/视图元素/产物引用；taskId 可选（缺省取最新任务，传了则查该次运行）")
    public R<PreprocessDetailVO> preprocessDetail(
            @Parameter(description = "文件结果ID", required = true) @PathVariable("fileResultId") Long fileResultId,
            @Parameter(description = "任务ID（可选，查历史运行详情）") @RequestParam(value = "taskId", required = false) Long taskId) {
        return R.ok(preprocessControlService.preprocessDetail(fileResultId, taskId));
    }
}
