package com.knowledge.common.dto.request.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 个人信息修改请求（本人操作）。
 *
 * <p>只能改真实姓名、邮箱、手机号；用户名、角色、状态与密码都不在此接口范围内。
 *
 * @author cxxl
 */
@Data
public class ProfileUpdateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 真实姓名 */
    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 64, message = "真实姓名长度不能超过 64 位")
    private String displayName;

    /** 邮箱（可空，空串表示清空） */
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱长度不能超过 128 位")
    private String email;

    /** 手机号（可空，空串表示清空，允许数字与 + - ( ) 空格） */
    @Pattern(regexp = "^$|^[0-9+()\\- ]{6,32}$", message = "手机号格式不正确")
    private String phone;
}
