package com.knowledge.biz.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.biz.service.FileSubmitService;
import com.knowledge.common.core.util.R;
import com.knowledge.common.dto.request.input.FileSubmitRequest;
import com.knowledge.common.dto.response.input.FileResultVO;
import com.knowledge.common.dto.response.input.FileSubmitResponse;
import com.knowledge.common.dto.response.input.SubmitLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文档输入 Controller（文件提交与查询入口）：薄转发，业务编排在 {@link FileSubmitService}。
 *
 * <p>接口清单：
 * <ul>
 *   <li>POST /knowledge-base/{id}/submit —— 提交文件（幂等键 requestId）</li>
 *   <li>GET  /knowledge-base/{id}/submit-logs —— 提交记录分页（含失败原因）</li>
 *   <li>GET  /knowledge-base/{id}/file-results —— 文件结果分页（环节状态列表）</li>
 * </ul>
 *
 * @author cxxl
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/knowledge-base")
@Tag(name = "文档输入", description = "文件提交、提交记录与文件结果列表")
public class KnowledgeFileInputController {

    private final FileSubmitService fileSubmitService;

    /**
     * 提交文件：对文件引用做归属校验与文件校验后完成建档三写
     * （kb_source_file / kb_file_result / kb_submit_log，同一事务）；
     * 本接口只建档，不创建处理任务——解析由页面在文件结果页手动触发（手动逐环节口径）。
     *
     * <p>幂等语义：以 requestId 为幂等键；同键重复请求返回首次提交记录，
     * 不重复建档（并发冲突时回滚本事务并回放已有记录）。
     *
     * @param id      知识库 ID（路径参数）
     * @param request 提交请求体：fileId + requestId（幂等键）
     * @return 提交响应：新建提交 pipelineTaskId 为 null；
     *         幂等回放时回填该文件结果已有的解析任务 ID（可能为 null）
     * @apiNote 失败语义：知识库不存在/未启用（40401/40421）与幂等键缺失（40420）抛业务异常；
     *          文件校验不通过（4041x，含文件不存在）不抛异常，正常返回 FAIL 提交日志
     *          （failReason 携带原因）。
     */
    @PostMapping("/{id}/submit")
    @Operation(summary = "提交文件", description = "提交文件引用：校验、建档三写；"
            + "解析由页面手动触发；幂等键 requestId")
    public R<FileSubmitResponse> submit(
            @Parameter(description = "知识库ID", required = true) @PathVariable("id") Long id,
            @Valid @RequestBody FileSubmitRequest request) {
        log.info("===> KnowledgeFileInputController submit 提交文件, kbId={}, fileId={}, requestId={}",
                id, request.getFileId(), request.getRequestId());
        return R.ok(fileSubmitService.submit(id, request));
    }

    /**
     * 提交记录分页查询：按知识库分页返回提交流水（PASS/FAIL 均记录，数据源 kb_submit_log），
     * 支持按提交结果与文件名模糊筛选；失败原因随记录一并返回。
     *
     * @param id       知识库 ID（路径参数）
     * @param current  当前页，从 1 开始（缺省 1）
     * @param size     每页条数（缺省 10）
     * @param status   提交结果筛选（PASS/FAIL，可选）
     * @param fileName 文件名模糊筛选（可选）
     * @return 提交记录分页 VO
     */
    @GetMapping("/{id}/submit-logs")
    @Operation(summary = "提交记录列表", description = "分页查询提交日志（含失败原因），支持 status/文件名筛选")
    public R<IPage<SubmitLogVO>> submitLogs(
            @Parameter(description = "知识库ID", required = true) @PathVariable("id") Long id,
            @Parameter(description = "当前页") @RequestParam(value = "current", defaultValue = "1") long current,
            @Parameter(description = "每页条数") @RequestParam(value = "size", defaultValue = "10") long size,
            @Parameter(description = "提交结果（PASS/FAIL）") @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "文件名（模糊查询）") @RequestParam(value = "fileName", required = false) String fileName) {
        return R.ok(fileSubmitService.pageSubmitLogs(current, size, id, status, fileName));
    }

    /**
     * 文件结果分页查询（执行链工作台列表数据源）：按知识库分页返回文件处理结果，
     * 每行附带各环节最新任务状态（PARSE/STRUCTURE/PREPROCESS/CHUNK/EMBED 五环节）。
     *
     * @param id      知识库 ID（路径参数）
     * @param current 当前页，从 1 开始（缺省 1）
     * @param size    每页条数（缺省 10）
     * @param stage   环节（可选；合法值 PARSE/STRUCTURE/PREPROCESS/CHUNK/EMBED，非法值 40001）
     * @return 文件结果分页 VO（含来源文件信息与各环节状态列表）
     */
    @GetMapping("/{id}/file-results")
    @Operation(summary = "文件结果列表", description = "分页查询文件结果（含各环节最新任务状态）；stage 可选")
    public R<IPage<FileResultVO>> fileResults(
            @Parameter(description = "知识库ID", required = true) @PathVariable("id") Long id,
            @Parameter(description = "当前页") @RequestParam(value = "current", defaultValue = "1") long current,
            @Parameter(description = "每页条数") @RequestParam(value = "size", defaultValue = "10") long size,
            @Parameter(description = "环节（可选）") @RequestParam(value = "stage", required = false) String stage) {
        return R.ok(fileSubmitService.pageFileResults(current, size, id, stage));
    }
}
