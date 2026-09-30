package com.knowledge.biz.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.biz.service.HomeService;
import com.knowledge.common.dto.response.R;
import com.knowledge.common.dto.request.home.HomeActivityQueryDto;
import com.knowledge.common.dto.request.home.HomeRecentSubmitQueryDto;
import com.knowledge.common.dto.response.home.HomeActivityVO;
import com.knowledge.common.dto.response.home.HomeRecentSubmitVO;
import com.knowledge.common.dto.response.home.HomeSummaryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页 Controller：资产速览 + 最近提交 + 行为记录。
 *
 * <p>独立于各业务 Controller：首页只读、跨模块聚合，接业务 Controller 会把
 * "单页职责"搅浑。数据来源是四块 —— 各资产表计数、`kb_submit_log`（最近提交）、
 * `kb_audit_log`（行为记录）。
 *
 * <p>刻意**不提供**环节分布与待处理清单：那些在各业务页有更准的口径，
 * 首页只回答"有多少资产"与"最近发生了什么"（文件提交 + 管理员动作）。
 *
 * <p>两个列表都用查询对象收参（{@link HomeActivityQueryDto} / {@link HomeRecentSubmitQueryDto}）：
 * 条件会随筛选需求增长，摊在签名上会越滚越长。
 * `@ParameterObject` 让 Swagger 把对象字段展开成独立查询参数。
 *
 * @author cxxl
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/home")
@Tag(name = "首页", description = "资产速览 + 最近提交 + 行为记录（审计动作）")
public class HomeController {

    private final HomeService homeService;

    @GetMapping("/summary")
    @Operation(summary = "资产速览", description = "知识库 / 策略版本（四类细分）/ 索引版本 / 文档提交")
    public R<HomeSummaryVO> summary() {
        return R.ok(homeService.summary());
    }

    @GetMapping("/activities")
    @Operation(summary = "行为记录", description = "分页查询审计动作")
    public R<IPage<HomeActivityVO>> activities(@ParameterObject @Valid HomeActivityQueryDto query) {
        return R.ok(homeService.activities(query));
    }

    @GetMapping("/recent-submits")
    @Operation(summary = "最近提交", description = "全库混合的文件提交记录，新→旧；附知识库名与提交人")
    public R<IPage<HomeRecentSubmitVO>> recentSubmits(@ParameterObject @Valid HomeRecentSubmitQueryDto query) {
        return R.ok(homeService.recentSubmits(query));
    }
}
