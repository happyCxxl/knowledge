package com.knowledge.biz.controller;

import com.knowledge.biz.service.ParseControlService;
import com.knowledge.common.core.util.R;
import com.knowledge.common.dto.response.parse.ParseDetailVO;
import com.knowledge.common.dto.response.task.StageTriggerVO;
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
 * 解析控制面 Controller（触发解析 + 解析详情；解析环节专属）。
 *
 * @author cxxl
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/file-results")
@Tag(name = "解析控制", description = "触发解析与解析详情")
public class KnowledgeFileParseController {

    private final ParseControlService parseControlService;

    /**
     * 触发解析（首次解析与失败重跑同一入口，手动逐环节）。
     *
     * <p>四分支：QUEUED 任务直接补投入队；RUNNING 拒绝（40431）；成功/部分成功拒绝（40437）；
     * FAILED/CANCELLED/无任务新建 PARSE 任务并入队。旧任务/旧产物保留；完成后停在终态，不自动触发组装。
     *
     * @param fileResultId 文件结果 ID（路径参数）
     * @return 触发响应（新登记/已有任务 ID）
     * @apiNote 文件结果不存在 40432；进行中 40431；成功后重跑 40437
     */
    @PostMapping("/{fileResultId}/parse")
    @Operation(summary = "触发解析", description = "触发解析/重跑（手动逐环节，首次解析与失败重跑同一入口）："
            + "QUEUED 任务直接入队、RUNNING 拒绝（40431）、成功/部分成功拒绝（40437）、FAILED/无任务新建入队；旧任务/旧产物保留")
    public R<StageTriggerVO> parse(
            @Parameter(description = "文件结果ID", required = true) @PathVariable("fileResultId") Long fileResultId) {
        log.info("===> KnowledgeFileParseController parse 触发解析, fileResultId={}", fileResultId);
        return R.ok(parseControlService.parse(fileResultId));
    }

    /**
     * 解析详情：任务状态 + 子步骤列表 + 产物引用/告警（告警从产物 JSON 提取）。
     *
     * @param fileResultId 文件结果 ID（路径参数）
     * @param taskId       任务 ID（可选；缺省取最新任务，传了则查该次历史运行）
     * @return 解析详情 VO（无任务时 taskId/步骤为空，告警为空列表）
     */
    @GetMapping("/{fileResultId}/parse-detail")
    @Operation(summary = "解析详情", description = "任务状态 + 子步骤列表 + 产物引用/告警；taskId 可选（缺省取最新任务，传了则查该次运行）")
    public R<ParseDetailVO> parseDetail(
            @Parameter(description = "文件结果ID", required = true) @PathVariable("fileResultId") Long fileResultId,
            @Parameter(description = "任务ID（可选，查历史运行详情）") @RequestParam(value = "taskId", required = false) Long taskId) {
        return R.ok(parseControlService.parseDetail(fileResultId, taskId));
    }
}
