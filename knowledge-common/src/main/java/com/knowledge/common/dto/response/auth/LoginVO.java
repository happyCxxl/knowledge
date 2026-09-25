package com.knowledge.common.dto.response.auth;

import lombok.Data;

/**
 * 登录结果。
 *
 * @author cxxl
 */
@Data
public class LoginVO {

    /** 访问令牌 */
    private String token;

    /** 角色码值：ADMIN 管理员 / USER 普通用户（前端据此渲染菜单） */
    private String role;
}
