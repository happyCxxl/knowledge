package com.knowledge.common.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.knowledge.infra.domain.base.BaseInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 用户（表：kb_user）。
 *
 * @author cxxl
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_user")
public class User extends BaseInfo {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键（雪花） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 登录用户名 */
    private String username;

    /** 密码（BCrypt 哈希） */
    private String password;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 角色码值：ADMIN 管理员 / USER 普通用户（见 UserRole） */
    private String role;

    /** 令牌版本：递增即让该账号已签发的令牌全部失效（改密码/改角色/停用/删除时 +1） */
    private Integer tokenVersion;
}
