package com.knowledge.common.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.knowledge.infra.domain.base.BaseInfo;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户（表：kb_user）。
 *
 * @author cxxl
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("kb_user")
public class User extends BaseInfo {

    /** 主键（雪花） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 登录用户名 */
    private String username;

    /** 密码（BCrypt 哈希） */
    private String password;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;
}
