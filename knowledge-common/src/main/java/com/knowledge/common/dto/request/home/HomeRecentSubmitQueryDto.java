package com.knowledge.common.dto.request.home;

import cn.hutool.core.util.ObjectUtil;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 首页「最近提交」查询条件。
 *
 * <p>目前只有分页。做成对象而不是两个 `@RequestParam`：后续要加筛选（按结果 / 按知识库）时
 * Controller 与 Service 签名都不用改。
 *
 * <p>可见范围不在这里：提交人由后端从安全上下文取，前端不传。
 *
 * @author cxxl
 */
@Data
public class HomeRecentSubmitQueryDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 默认页码 */
    private static final long DEFAULT_CURRENT = 1L;

    /** 默认每页条数（首页前端总是显式传算好的页长，这里只兜底） */
    private static final long DEFAULT_SIZE = 10L;

    /** 当前页（可空，按默认 1） */
    private Long current;

    /** 每页条数（可空，按默认 10） */
    private Long size;

    /** 页码兜底：不传或非法值按默认，避免分页拿到 null */
    public long currentOrDefault() {
        return ObjectUtil.isNull(current) || current < 1 ? DEFAULT_CURRENT : current;
    }

    /** 每页条数兜底 */
    public long sizeOrDefault() {
        return ObjectUtil.isNull(size) || size < 1 ? DEFAULT_SIZE : size;
    }
}
