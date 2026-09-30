package com.knowledge.common.dto.response.auth;

import lombok.Data;

/**
 * 登录结果。
 *
 * <p>只带令牌里没有、界面又需要的用户信息：角色与用户 ID 可从令牌载荷解析，不重复下发。
 *
 * @author cxxl
 */
@Data
public class LoginVO {

    /** 访问令牌 */
    private String token;

    /** 真实姓名（顶栏展示用） */
    private String displayName;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;
}
