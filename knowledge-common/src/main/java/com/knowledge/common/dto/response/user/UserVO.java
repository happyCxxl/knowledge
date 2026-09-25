package com.knowledge.common.dto.response.user;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户视图对象（status 返回码值 1/0；不含密码字段）。
 *
 * @author cxxl
 */
@Data
public class UserVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（雪花 ID，前端按字符串处理防精度丢失） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 登录用户名 */
    private String username;

    /** 状态码值：1 启用 / 0 停用 */
    private Integer status;

    /** 角色码值：ADMIN 管理员 / USER 普通用户 */
    private String role;

    private String createBy;

    private LocalDateTime createTime;

    private String updateBy;

    private LocalDateTime updateTime;
}
