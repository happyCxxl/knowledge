package com.knowledge.biz.controller;

import com.knowledge.common.core.util.R;
import com.knowledge.biz.service.StageContentQueryService;
import com.knowledge.common.dto.response.stagecontent.StageContentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 产物内容 Controller（step-12 B2，恢复 T3）：对比视图内容层数据源。
 *
 * @author cxxl
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/file-results")
@Tag(name = "产物内容", description = "环节产物内容（对比视图内容层）")
public class StageContentController {

    private final StageContentQueryService stageContentQueryService;

    @GetMapping("/{fileResultId}/stage-content")
    @Operation(summary = "产物内容", description = "白名单 PARSE/STRUCTURE/PREPROCESS/CHUNK/EMBED（非法 40001）；"
            + "按 task.productId 精确取该次运行产物，历史任务同样可展示（latest=该次运行产物是否可用）；taskId 可选（缺省最新任务）")
    public R<StageContentVO> stageContent(
            @Parameter(description = "文件结果ID", required = true) @PathVariable("fileResultId") Long fileResultId,
            @Parameter(description = "环节（PARSE/STRUCTURE/PREPROCESS/CHUNK/EMBED）", required = true)
            @RequestParam("stage") String stage,
            @Parameter(description = "任务ID（可选，查指定运行）") @RequestParam(value = "taskId", required = false) Long taskId) {
        return R.ok(stageContentQueryService.stageContent(fileResultId, stage, taskId));
    }
}
