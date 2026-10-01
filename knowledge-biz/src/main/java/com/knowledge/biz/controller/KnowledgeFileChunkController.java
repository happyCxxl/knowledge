package com.knowledge.biz.controller;

import com.knowledge.biz.service.ChunkControlService;
import com.knowledge.common.dto.request.stage.StageDetailQueryDto;
import com.knowledge.common.dto.request.stage.StageTriggerQueryDto;
import com.knowledge.common.dto.response.R;
import com.knowledge.common.dto.response.chunk.ChunkDetailVO;
import com.knowledge.common.dto.response.chunk.ChunkTriggerVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 切片控制面 Controller（手动逐环节触发点）。
 *
 * @author cxxl
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/file-results")
@Tag(name = "切片控制", description = "触发切片与重跑")
public class KnowledgeFileChunkController {

    private final ChunkControlService chunkControlService;

    @PostMapping("/{fileResultId}/chunk")
    @Operation(summary = "触发切片", description = "触发切片/重跑（手动逐环节）；"
            + "strategyVersionId 可选（策略行 ID，缺省取库内启用中最新版本）；upstreamProductId 可选（指定上游 PREPROCESS 产物，缺省取最新）；进行中任务拒绝（40431）")
    public R<ChunkTriggerVO> chunk(
            @Parameter(description = "文件结果ID", required = true) @PathVariable("fileResultId") Long fileResultId,
            @ParameterObject StageTriggerQueryDto query) {
        log.info("===> KnowledgeFileChunkController chunk 触发切片, fileResultId={}, strategyVersionId={}, upstreamProductId={}",
                fileResultId, query.getStrategyVersionId(), query.getUpstreamProductId());
        return R.ok(chunkControlService.chunk(fileResultId, query.getStrategyVersionId(), query.getUpstreamProductId()));
    }

    @GetMapping("/{fileResultId}/chunk-detail")
    @Operation(summary = "切片详情", description = "任务状态 + 子步骤列表 + 切片统计/切片列表/产物引用（kb_chunk 数据源）；taskId 可选（缺省取最新任务，传了则查该次运行）")
    public R<ChunkDetailVO> chunkDetail(
            @Parameter(description = "文件结果ID", required = true) @PathVariable("fileResultId") Long fileResultId,
            @ParameterObject StageDetailQueryDto query) {
        return R.ok(chunkControlService.chunkDetail(fileResultId, query.getTaskId()));
    }
}
