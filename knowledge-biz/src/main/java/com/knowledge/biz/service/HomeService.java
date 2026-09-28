package com.knowledge.biz.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.knowledge.common.dto.request.home.HomeActivityQueryDto;
import com.knowledge.common.dto.request.home.HomeRecentSubmitQueryDto;
import com.knowledge.common.dto.response.home.HomeActivityVO;
import com.knowledge.common.dto.response.home.HomeRecentSubmitVO;
import com.knowledge.common.dto.response.home.HomeSummaryVO;

/**
 * 首页服务：资产速览 + 最近提交 + 行为记录。
 *
 * <p>首页只承担两件事：**有多少资产** 与 **最近发生了什么**。
 * "最近发生"分两层：**文件提交**（日常操作，在 `kb_submit_log`）
 * 与 **管理动作**（在 `kb_audit_log`）。两者口径不同，所以分成两块展示，不混在一张表里。
 *
 * @author cxxl
 */
public interface HomeService {

    /**
     * 资产速览：知识库 / 策略版本（四类细分）/ 索引版本 / 文档提交。
     */
    HomeSummaryVO summary();

    /**
     * 最近提交：全库混合的文件提交记录（新→旧），附所属知识库名与提交人。
     *
     * <p>`status` 是**提交校验结果**（PASS/FAIL），不是处理链进度。
     *
     * @param query 分页条件
     */
    IPage<HomeRecentSubmitVO> recentSubmits(HomeRecentSubmitQueryDto query);

    /**
     * 行为记录：分页查询审计动作（新→旧），并把动作与对象翻译成可读文本。
     *
     * <p>数据源是 `kb_audit_log`，**只含管理员关键操作**；
     * 文件提交行为见 {@link #recentSubmits}。
     *
     * @param query 查询条件（分页 + 动作 / 操作人 / 时间段；条件可空，空即不过滤）
     */
    IPage<HomeActivityVO> activities(HomeActivityQueryDto query);
}
