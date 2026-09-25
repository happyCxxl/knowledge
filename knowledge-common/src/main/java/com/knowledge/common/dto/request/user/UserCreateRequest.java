package com.knowledge.common.dto.request.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 新增用户请求（管理员操作）。
 *
 * <p>角色可不传，缺省为普通用户；状态可不传，缺省启用。
 *
 * @author cxxl
 */
@Data
public class UserCreateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 登录用户名（3-32 位，全局唯一） */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度须为 3-32 位")
    private String username;

    /** 初始密码（明文，落库前 BCrypt 加密） */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度须为 6-64 位")
    private String password;

    /** 角色码值：ADMIN / USER（缺省 USER） */
    private String role;

    /** 状态：1 启用 / 0 停用（缺省 1） */
    private Integer status;
}
