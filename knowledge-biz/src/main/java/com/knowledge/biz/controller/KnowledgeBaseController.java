package com.knowledge.biz.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.biz.service.KnowledgeBaseService;
import com.knowledge.common.core.util.R;
import com.knowledge.common.dto.request.knowledge.KnowledgeBaseCreateDto;
import com.knowledge.common.dto.request.knowledge.KnowledgeBaseUpdateDto;
import com.knowledge.common.dto.request.knowledge.StrategyBindingUpdateDto;
import com.knowledge.common.dto.request.knowledge.StrategyBindingsUpdateRequest;
import com.knowledge.common.dto.response.knowledge.KnowledgeBaseVO;
import com.knowledge.common.dto.response.knowledge.StrategyBindingVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 知识库管理接口。
 *
 * @author cxxl
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/knowledge-base")
@Tag(name = "知识库管理", description = "知识库管理闭环接口")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @PostMapping
    @Operation(summary = "创建知识库", description = "创建一个新的知识库，默认启用状态")
    public R<Long> create(@Valid @RequestBody KnowledgeBaseCreateDto dto) {
        log.info("===> KnowledgeBaseController create 创建知识库, name={}", dto.getName());
        return R.ok(knowledgeBaseService.create(dto), "创建成功");
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新知识库", description = "更新知识库名称与描述")
    public R<Boolean> update(
            @Parameter(description = "知识库ID", required = true) @PathVariable("id") Long id,
            @Valid @RequestBody KnowledgeBaseUpdateDto dto) {
        dto.setId(id);
        log.info("===> KnowledgeBaseController update 更新知识库, id={}", id);
        return R.ok(knowledgeBaseService.update(dto), "更新成功");
    }

    @GetMapping("/{id}")
    @Operation(summary = "知识库详情", description = "查询单个知识库详情（已删除不可见）")
    public R<KnowledgeBaseVO> detail(
            @Parameter(description = "知识库ID", required = true) @PathVariable("id") Long id) {
        return R.ok(knowledgeBaseService.detail(id));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询知识库", description = "分页列表，支持名称模糊查询，不含已删除")
    public R<IPage<KnowledgeBaseVO>> page(
            @Parameter(description = "当前页") @RequestParam(value = "current", defaultValue = "1") long current,
            @Parameter(description = "每页条数") @RequestParam(value = "size", defaultValue = "10") long size,
            @Parameter(description = "名称（模糊查询）") @RequestParam(value = "name", required = false) String name) {
        return R.ok(knowledgeBaseService.page(current, size, name));
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

    @GetMapping("/{id}/strategy-binding")
    @Operation(summary = "查询知识库策略绑定", description = "按策略类型查知识库绑定（未绑定返回仅含 strategyType）")
    public R<StrategyBindingVO> strategyBinding(
            @Parameter(description = "知识库ID", required = true) @PathVariable("id") Long id,
            @Parameter(description = "策略类型（CHUNK）", required = true) @RequestParam("strategyType") String strategyType) {
        return R.ok(knowledgeBaseService.strategyBinding(id, strategyType));
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
