package com.knowledge.biz.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.biz.service.KnowledgeHomeService;
import com.knowledge.common.dto.response.R;
import com.knowledge.common.dto.request.home.HomeRecentSubmitQueryDto;
import com.knowledge.common.dto.response.home.HomeRecentSubmitVO;
import com.knowledge.common.dto.response.home.HomeSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页 Controller：资产速览 + 最近提交。
 *
 * <p>独立于各业务 Controller：首页只读、跨模块聚合，接业务 Controller 会把
 * "单页职责"搅浑。数据来源是各资产表计数与 `kb_submit_log`（最近提交）。
 *
 * <p>**最近提交只返回当前登录用户的记录**：接口不接受"提交人"参数，
 * 提交人由服务端从安全上下文取，避免前端传谁就查谁。
 *
 * <p>刻意**不提供**环节分布、待处理清单与审计动作：前者在各业务页有更准的口径，
 * 后者属于管理视角，不在首页展示。
 *
 * <p>查询对象收参（{@link HomeRecentSubmitQueryDto}）：条件会随筛选需求增长，
 * 摊在签名上会越滚越长。`@ParameterObject` 让 Swagger 把对象字段展开成独立查询参数。
 *
 * <p>分页条件不加 `@Valid`：DTO 没有约束注解，非法值由 DTO 自己按默认兜底
 * （见 `currentOrDefault` / `sizeOrDefault`），加了只会给出"这里已校验"的错误暗示。
 *
 * @author cxxl
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/home")
@Tag(name = "首页", description = "资产速览 + 最近提交（仅本人）")
public class KnowledgeHomeController {

    private final KnowledgeHomeService knowledgeHomeService;

    @GetMapping("/summary")
    @Operation(summary = "资产速览", description = "知识库（总数 + 启用数）/ 可用策略版本（四类细分）/ 已建档文档数")
    public R<HomeSummaryVO> summary() {
        return R.ok(knowledgeHomeService.summary());
    }

    @GetMapping("/recent-submits")
    @Operation(summary = "最近提交", description = "当前登录用户的文件提交记录，新→旧；附知识库名与文件类型")
    public R<IPage<HomeRecentSubmitVO>> recentSubmits(@ParameterObject HomeRecentSubmitQueryDto query) {
        return R.ok(knowledgeHomeService.recentSubmits(query));
    }
}
