package com.knowledge.common.dto.request.user;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 编辑用户请求（管理员操作）。
 *
 * <p>用户名不可修改：它是登录凭据与留痕口径，改动会让历史记录对不上。
 * 密码不传表示不重置；角色与状态不传表示保持不变。
 *
 * @author cxxl
 */
@Data
public class UserUpdateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 新密码（明文，不传表示不重置） */
    @Size(min = 6, max = 64, message = "密码长度须为 6-64 位")
    private String password;

    /** 角色码值：ADMIN / USER（不传表示不变） */
    private String role;

    /** 状态：1 启用 / 0 停用（不传表示不变） */
    private Integer status;
}
