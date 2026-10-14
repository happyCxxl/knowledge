package com.knowledge.biz.controller;

import com.knowledge.biz.service.ModelAdminService;
import com.knowledge.common.annotation.AdminOnly;
import com.knowledge.common.dto.request.model.ModelQueryDto;
import com.knowledge.common.dto.request.model.ModelSaveDto;
import com.knowledge.common.dto.request.model.ModelStatusDto;
import com.knowledge.common.dto.response.R;
import com.knowledge.common.dto.response.model.ModelItemVO;
import com.knowledge.common.dto.response.model.ModelProbeVO;
import com.knowledge.common.dto.response.model.ModelVendorVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模型管理 Controller：模型目录的清单、新增、编辑、启停、连通性测试与厂商只读清单，全部仅管理员可用。
 *
 * <p>路径前缀 `/admin/model`（与用量两个只读接口同前缀，仓库既有接口不带 `api` 段）。
 * 厂商连接与密钥都在配置（`knowledge.model.vendors`），本 Controller **没有密钥相关的入口**：
 * `/vendors` 只读，只给出「该厂商是否已配密钥」，密钥内容不出接口。
 *
 * <p>查询条件成组时收进 {@link ModelQueryDto} 并用 `@ParameterObject` 展开成独立查询参数。
 *
 * @author cxxl
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/model")
@Tag(name = "模型管理", description = "模型目录的清单、写入、启停与连通性测试（仅管理员）")
public class ModelAdminController {

    private final ModelAdminService modelAdminService;

    @GetMapping("/models")
    @AdminOnly
    @Operation(summary = "模型清单",
            description = "按能力（字典名）/ 状态（ENABLED / DISABLED）/ 关键字（匹配模型名或展示名）过滤，"
                    + "返回目录视图字段：厂商、模型、展示名、能力多值、上下文窗口、四个价格列、币种、两个限额、"
                    + "状态、生效区间、最近一次连通性测试结果与备注")
    public R<List<ModelItemVO>> models(@ParameterObject ModelQueryDto query) {
        return R.ok(modelAdminService.list(query));
    }

    @PostMapping("/models")
    @AdminOnly
    @Operation(summary = "新增模型",
            description = "校验厂商码在配置里、唯一键 (vendor_code, model_key) 不冲突、价格与限额非负、"
                    + "能力与状态取值在字典内、生效区间开始早于结束；展示名留空取模型名")
    public R<ModelItemVO> create(@RequestBody ModelSaveDto dto) {
        return R.ok(modelAdminService.create(dto), "新增成功");
    }

    @PutMapping("/models/{id}")
    @AdminOnly
    @Operation(summary = "编辑模型",
            description = "覆盖写业务列；校验口径与新增一致，唯一键判定排除本行；价格与限额清空即写空")
    public R<ModelItemVO> update(@PathVariable Long id, @RequestBody ModelSaveDto dto) {
        return R.ok(modelAdminService.update(id, dto), "保存成功");
    }

    @PatchMapping("/models/{id}/status")
    @AdminOnly
    @Operation(summary = "启停模型", description = "只改状态列，取值 ENABLED / DISABLED")
    public R<ModelItemVO> updateStatus(@PathVariable Long id, @RequestBody ModelStatusDto dto) {
        return R.ok(modelAdminService.updateStatus(id, dto), "状态已更新");
    }

    @PostMapping("/models/{id}/probe")
    @AdminOnly
    @Operation(summary = "连通性测试",
            description = "按模型声明的能力发一次最小真实请求（对话 / 向量 / 视觉），返回可用性、耗时与响应回传的"
                    + "模型标识；成功与失败都写回 probe_at / probe_result，失败原因经 errorCode 与 message 给出")
    public R<ModelProbeVO> probe(@PathVariable Long id) {
        return R.ok(modelAdminService.probe(id));
    }

    @GetMapping("/vendors")
    @AdminOnly
    @Operation(summary = "厂商清单（只读）",
            description = "配置 knowledge.model.vendors 里的厂商：码、展示名、是否已配密钥（布尔）与是否唯一厂商；"
                    + "只读接口，不提供新增与编辑，密钥内容不下发")
    public R<List<ModelVendorVO>> vendors() {
        return R.ok(modelAdminService.vendors());
    }
}
