package com.knowledge.common.dto.request.home;

import com.knowledge.common.dto.request.page.PageQueryDto;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 首页「最近提交」查询条件。
 *
 * <p>目前只有分页（页码与页长口径见 {@link PageQueryDto}）。做成对象而不是两个 `@RequestParam`：
 * 后续要加筛选（按结果 / 按知识库）时 Controller 与 Service 签名都不用改。
 *
 * <p>可见范围不在这里：提交人由后端从安全上下文取，前端不传。
 *
 * @author cxxl
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class HomeRecentSubmitQueryDto extends PageQueryDto {

    @Serial
    private static final long serialVersionUID = 1L;
}
