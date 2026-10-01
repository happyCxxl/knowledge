package com.knowledge.common.dto.request.page;

import cn.hutool.core.util.ObjectUtil;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分页查询条件（列表类接口共用）。
 *
 * <p>与首页最近提交同一口径：页码与页长都可空，取默认值时才兜底，避免分页拿到 null/0。
 * 前端列表页总是显式传值，这里只兜底异常输入。
 *
 * @author cxxl
 */
@Data
public class PageQueryDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 默认页码 */
    private static final long DEFAULT_CURRENT = 1L;

    /** 默认每页条数 */
    private static final long DEFAULT_SIZE = 10L;

    /** 当前页（可空，按默认 1） */
    private Long current;

    /** 每页条数（可空，按默认 10） */
    private Long size;

    /** 页码兜底：不传或非法值按默认，避免分页拿到 null/0 */
    public long currentOrDefault() {
        return ObjectUtil.isNull(current) || current < 1 ? DEFAULT_CURRENT : current;
    }

    /** 每页条数兜底 */
    public long sizeOrDefault() {
        return ObjectUtil.isNull(size) || size < 1 ? DEFAULT_SIZE : size;
    }
}
