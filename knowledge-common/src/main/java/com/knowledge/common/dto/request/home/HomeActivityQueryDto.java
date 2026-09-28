package com.knowledge.common.dto.request.home;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 首页行为记录查询条件。
 *
 * <p>作为 GET 的查询对象收参：条件会随筛选需求增长（分页 / 动作 / 操作人 / 时间段），
 * 摊在方法签名上会越滚越长，Controller 与 Service 两层都要跟着改。
 *
 * <p>所有条件都可空，**空即不过滤**；时间格式 `yyyy-MM-dd HH:mm:ss`。
 *
 * <p>不加 swagger 注解：knowledge-common 没有 springdoc 依赖，本模块的 DTO 一律不引它。
 * 查询参数的文档由 Controller 的 `@Operation` 说明。
 *
 * @author cxxl
 */
@Data
public class HomeActivityQueryDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 默认页码 */
    private static final long DEFAULT_CURRENT = 1L;

    /** 默认每页条数 */
    private static final long DEFAULT_SIZE = 20L;

    /** 当前页（可空，按默认 1） */
    private Long current;

    /** 每页条数（可空，按默认 20） */
    private Long size;

    /** 动作类型：PUBLISH_INDEX / ROLLBACK_INDEX / BIND / CREATE …；不传不过滤 */
    private String actionType;

    /** 操作人用户名；不传不过滤 */
    private String operator;

    /** 起始时间（含） */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime beginTime;

    /** 结束时间（含） */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /** 页码兜底：外部不传或传非法值时按默认，避免 MyBatis-Plus 分页拿到 null */
    public long currentOrDefault() {
        return current == null || current < 1 ? DEFAULT_CURRENT : current;
    }

    /** 每页条数兜底 */
    public long sizeOrDefault() {
        return size == null || size < 1 ? DEFAULT_SIZE : size;
    }
}
