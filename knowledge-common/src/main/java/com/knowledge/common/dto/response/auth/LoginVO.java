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
}
