package com.knowledge.biz.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.biz.service.FileSubmitService;
import com.knowledge.biz.service.KnowledgeBaseService;
import com.knowledge.common.dto.response.R;
import com.knowledge.common.dto.request.input.FileSubmitRequest;
import com.knowledge.common.dto.request.knowledge.KnowledgeBaseCreateDto;
import com.knowledge.common.dto.request.knowledge.KnowledgeBaseUpdateDto;
import com.knowledge.common.dto.request.knowledge.StrategyBindingUpdateDto;
import com.knowledge.common.dto.request.knowledge.StrategyBindingsUpdateRequest;
import com.knowledge.common.dto.request.page.PageQueryDto;
import com.knowledge.common.dto.response.input.FileResultVO;
import com.knowledge.common.dto.response.input.FileSubmitResponse;
import com.knowledge.common.dto.response.input.SubmitLogVO;
import com.knowledge.common.dto.response.knowledge.KnowledgeBaseStatsVO;
import com.knowledge.common.dto.response.knowledge.KnowledgeBaseVO;
import com.knowledge.common.dto.response.knowledge.StrategyBindingVO;
import com.knowledge.common.enums.knowledge.KnowledgeBaseSort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库管理接口。
 *
 * <p>**文档与知识库是同一个域**：文件提交（建档）、提交记录与文件结果查询三条接口也在这里
 * —— 原来是独立的 `KnowledgeFileInputController`，但它的路径本来就挂在 `/knowledge-base/{id}/**` 下，
 * 归属与生命周期都由知识库决定，拆成两个类只让"一个资源两处入口"。合并后**路径一字未改**
 * （前端无需改动）。业务编排仍在 `FileSubmitService`，本类只做薄转发。
 *
 * <p>**可见范围**：普通用户只读写自己创建的知识库，管理员不限归属。分页与统计按当前用户
 * 的归属过滤（不接受"创建人"参数 —— 前端传谁就查谁等于没做隔离）；所有按 ID 的接口
 * 都校验归属，**不可访问时返回 40401「知识库不存在」而不是 40104**：后者等于确认该库存在，
 * 会把别人的库 ID 变成可枚举、可探测的信息。口径实现在
 * `KnowledgeBaseRules.visibleOwnerId` / `checkAccessible`。
 *
 * @author cxxl
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/knowledge-base")
@Tag(name = "知识库管理", description = "知识库管理闭环接口（含文档提交、提交记录与文件结果查询）")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    private final FileSubmitService fileSubmitService;

    /**
     * 创建知识库。
     *
     * <p>**返回 String 而不是 Long**：雪花 ID 有 19 位，超过 JS 的
     * `Number.MAX_SAFE_INTEGER`（2^53-1 ≈ 9.0e15），而 Jackson 对裸 Long 会序列化成
     * **不带引号的数字**，前端 `JSON.parse` 按 Number 读会丢精度 —— 实测
     * `2104612193694224386` 被读成 `2104612193694220000`（末 4 位归零）。
     *
     * <p>这个坑的实际后果：前端拿到失真 ID 后用去调绑定接口，后端查不到该库而报
     * 「知识库不存在」—— 表现为"新建时勾选绑定策略就失败"。改为 String 后前端拿到的是
     * 精确的 19 位串。项目里其它雪花 ID 字段（如 StrategyBindingVO）也是这么处理的。
     */
    @PostMapping
    @Operation(summary = "创建知识库", description = "创建一个新的知识库，默认启用状态、归属为当前登录用户；返回新库 ID（字符串）")
    public R<String> create(@Valid @RequestBody KnowledgeBaseCreateDto dto) {
        log.info("===> KnowledgeBaseController create 创建知识库, name={}", dto.getName());
        return R.ok(String.valueOf(knowledgeBaseService.create(dto)), "创建成功");
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新知识库", description = "更新知识库名称、业务场景说明与策略绑定开关；仅本人可访问的库可改")
    public R<Boolean> update(
            @Parameter(description = "知识库ID", required = true) @PathVariable("id") Long id,
            @Valid @RequestBody KnowledgeBaseUpdateDto dto) {
        dto.setId(id);
        log.info("===> KnowledgeBaseController update 更新知识库, id={}", id);
        return R.ok(knowledgeBaseService.update(dto), "更新成功");
    }

    @GetMapping("/{id}")
    @Operation(summary = "知识库详情", description = "查询单个知识库详情（已删除不可见；非本人可访问的按「不存在」处理，返回 40401）")
    public R<KnowledgeBaseVO> detail(
            @Parameter(description = "知识库ID", required = true) @PathVariable("id") Long id) {
        return R.ok(knowledgeBaseService.detail(id));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询知识库", description = "分页列表，支持名称模糊查询、状态过滤与排序，不含已删除")
    public R<IPage<KnowledgeBaseVO>> page(
            @ParameterObject PageQueryDto pageQuery,
            @Parameter(description = "名称（模糊查询）") @RequestParam(value = "name", required = false) String name,
            @Parameter(description = "状态：1 启用 / 0 停用；不传不过滤")
            @RequestParam(value = "status", required = false) Integer status,
            @Parameter(description = "排序口径：DEFAULT 默认 / UPDATED 最近更新 / NAME 名称；未知值按默认")
            @RequestParam(value = "sort", required = false) String sort) {
        return R.ok(knowledgeBaseService.page(pageQuery.currentOrDefault(), pageQuery.sizeOrDefault(), name, status,
                KnowledgeBaseSort.of(sort)));
    }

    @GetMapping("/stats")
    @Operation(summary = "知识库统计概览", description = "知识库总数、启用数、文档总数；仅统计未删除数据")
    public R<KnowledgeBaseStatsVO> stats() {
        return R.ok(knowledgeBaseService.stats());
    }

    @PostMapping("/{id}/disable")
    @Operation(summary = "停用知识库", description = "停用后拒绝新文件接入与检索；仅启用状态可停用")
    public R<Boolean> disable(
            @Parameter(description = "知识库ID", required = true) @PathVariable("id") Long id) {
        log.info("===> KnowledgeBaseController disable 停用知识库, id={}", id);
        return R.ok(knowledgeBaseService.disable(id), "停用成功");
    }

    @PostMapping("/{id}/enable")
    @Operation(summary = "启用知识库", description = "重新启用；仅停用状态可启用")
    public R<Boolean> enable(
            @Parameter(description = "知识库ID", required = true) @PathVariable("id") Long id) {
        log.info("===> KnowledgeBaseController enable 启用知识库, id={}", id);
        return R.ok(knowledgeBaseService.enable(id), "启用成功");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除知识库", description = "逻辑删除，无恢复接口")
    public R<Boolean> delete(
            @Parameter(description = "知识库ID", required = true) @PathVariable("id") Long id) {
        log.info("===> KnowledgeBaseController delete 删除知识库, id={}", id);
        return R.ok(knowledgeBaseService.delete(id), "删除成功");
    }


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
     * @return 提交响应：新建提交 pipelineTaskId 为 null；幂等回放时回填该文件结果已有的解析任务 ID（可能为 null）
     */
    @PostMapping("/{id}/submit")
    @Operation(summary = "提交文件", description = "提交文件引用：校验、建档三写；"
            + "解析由页面手动触发；幂等键 requestId")
    public R<FileSubmitResponse> submit(
            @Parameter(description = "知识库ID", required = true) @PathVariable("id") Long id,
            @Valid @RequestBody FileSubmitRequest request) {
        log.info("===> KnowledgeBaseController submit 提交文件, kbId={}, fileId={}, requestId={}",
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
            @ParameterObject PageQueryDto pageQuery,
            @Parameter(description = "提交结果（PASS/FAIL）") @RequestParam(value = "status", required = false) String status,
            @Parameter(description = "文件名（模糊查询）") @RequestParam(value = "fileName", required = false) String fileName) {
        return R.ok(fileSubmitService.pageSubmitLogs(pageQuery.currentOrDefault(), pageQuery.sizeOrDefault(), id,
                status, fileName));
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
            @ParameterObject PageQueryDto pageQuery,
            @Parameter(description = "环节（可选）") @RequestParam(value = "stage", required = false) String stage) {
        return R.ok(fileSubmitService.pageFileResults(pageQuery.currentOrDefault(), pageQuery.sizeOrDefault(), id,
                stage));
    }

    @GetMapping("/{id}/strategy-binding")
    @Operation(summary = "查询知识库策略绑定", description = "按策略类型查知识库绑定（未绑定返回仅含 strategyType）")
    public R<StrategyBindingVO> strategyBinding(
            @Parameter(description = "知识库ID", required = true) @PathVariable("id") Long id,
            @Parameter(description = "策略类型（CHUNK）", required = true) @RequestParam("strategyType") String strategyType) {
        return R.ok(knowledgeBaseService.strategyBinding(id, strategyType));
    }

    @GetMapping("/strategy-bindings")
    @Operation(summary = "批量查询策略绑定",
            description = "按策略类型查所有已绑定的知识库（一次查询，每条含 knowledgeBaseId）")
    public R<List<StrategyBindingVO>> strategyBindings(
            @Parameter(description = "策略类型（CHUNK）", required = true) @RequestParam("strategyType") String strategyType) {
        return R.ok(knowledgeBaseService.strategyBindings(strategyType));
    }

    @PutMapping("/{id}/strategy-binding")
    @Operation(summary = "绑定/解绑知识库策略", description = "strategyVersionId 为 null 解绑；绑定校验类型/版本/启用状态")
    public R<Boolean> bindStrategy(
            @Parameter(description = "知识库ID", required = true) @PathVariable("id") Long id,
            @Valid @RequestBody StrategyBindingUpdateDto dto) {
        log.info("===> KnowledgeBaseController bindStrategy 绑定知识库策略, id={}, type={}", id, dto.getStrategyType());
        return R.ok(knowledgeBaseService.bindStrategy(id, dto), "绑定成功");
    }

    @PutMapping("/{id}/strategy-bindings")
    @Operation(summary = "批量设置知识库策略集合", description = "一次调用设置整套（发布=知识库策略集合）；strategyVersionId 为 null 解绑")
    public R<Boolean> bindStrategies(
            @Parameter(description = "知识库ID", required = true) @PathVariable("id") Long id,
            @Valid @RequestBody StrategyBindingsUpdateRequest request) {
        log.info("===> KnowledgeBaseController bindStrategies 批量绑定知识库策略集合, id={}", id);
        return R.ok(knowledgeBaseService.bindStrategies(id, request), "绑定成功");
    }
}
