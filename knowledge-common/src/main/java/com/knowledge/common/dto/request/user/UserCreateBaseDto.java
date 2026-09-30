package com.knowledge.common.dto.request.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 创建账号类请求的公共字段：自助注册与管理员新增共用。
 *
 * @author cxxl
 */
@Data
public abstract class UserCreateBaseDto implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 登录用户名（3-32 位，全局唯一） */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名长度须为 3-32 位")
    private String username;

    /** 真实姓名 */
    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 64, message = "真实姓名长度不能超过 64 位")
    private String displayName;

    /** 邮箱（可空） */
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128 位")
    private String email;

    /** 手机号（可空，允许数字与 + - ( ) 空格） */
    @Pattern(regexp = "^$|^[0-9+()\\- ]{6,32}$", message = "手机号格式不正确")
    private String phone;

    /** 密码（8-10 位） */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 10, message = "密码长度须为 8-10 位")
    private String password;
}
