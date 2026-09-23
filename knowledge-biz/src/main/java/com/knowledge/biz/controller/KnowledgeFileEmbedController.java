package com.knowledge.biz.controller;

import com.knowledge.common.core.util.R;
import com.knowledge.biz.service.EmbedControlService;
import com.knowledge.common.dto.response.embed.EmbedDetailVO;
import com.knowledge.common.dto.response.embed.EmbedTriggerVO;
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
 * 向量化控制面 Controller（手动逐环节触发点）。
 * 接口全部走平台资源服务器鉴权（需 Bearer token）。
 *
 * @author cxxl
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/file-results")
@Tag(name = "向量化控制", description = "触发向量化与重跑")
public class KnowledgeFileEmbedController {

    private final EmbedControlService embedControlService;

    @PostMapping("/{fileResultId}/embed")
    @Operation(summary = "触发向量化", description = "触发向量化/重跑（手动逐环节）；"
            + "strategyVersionId 可选（策略行 ID，缺省 KB 绑定优先，再取库内启用中最新）；upstreamProductId 可选（指定上游 CHUNK 产物，缺省取最新）；"
            + "无切片产物 40434、窗口不兼容 40435、进行中 40431")
    public R<EmbedTriggerVO> embed(
            @Parameter(description = "文件结果ID", required = true) @PathVariable("fileResultId") Long fileResultId,
            @Parameter(description = "策略版本行 ID（可选）")
            @RequestParam(value = "strategyVersionId", required = false) Long strategyVersionId,
            @Parameter(description = "上游产物ID（可选，指定 CHUNK 产物）")
            @RequestParam(value = "upstreamProductId", required = false) Long upstreamProductId) {
        log.info("===> KnowledgeFileEmbedController embed 触发向量化, fileResultId={}, strategyVersionId={}, upstreamProductId={}",
                fileResultId, strategyVersionId, upstreamProductId);
        return R.ok(embedControlService.embed(fileResultId, strategyVersionId, upstreamProductId));
    }

    @GetMapping("/{fileResultId}/embed-detail")
    @Operation(summary = "向量化详情", description = "任务状态 + 子步骤列表 + 集合摘要/记录 + 产物引用"
            + "（kb_embedding_set/kb_embedding_record 数据源，向量本体不下发）；taskId 可选（缺省取最新任务）")
    public R<EmbedDetailVO> embedDetail(
            @Parameter(description = "文件结果ID", required = true) @PathVariable("fileResultId") Long fileResultId,
            @Parameter(description = "任务ID（可选，查历史运行详情）") @RequestParam(value = "taskId", required = false) Long taskId) {
        return R.ok(embedControlService.embedDetail(fileResultId, taskId));
    }
}
