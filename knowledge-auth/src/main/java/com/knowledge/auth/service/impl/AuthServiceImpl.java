package com.knowledge.auth.service.impl;

import com.knowledge.auth.db.UserDbService;
import com.knowledge.auth.service.AuthService;
import com.knowledge.auth.util.JwtUtil;
import com.knowledge.common.domain.entity.User;
import com.knowledge.common.dto.request.auth.LoginRequest;
import com.knowledge.common.dto.request.auth.RegisterRequest;
import com.knowledge.common.dto.response.auth.LoginVO;
import com.knowledge.common.enums.user.UserRole;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.ThrowUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现。
 *
 * @author cxxl
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserDbService userDbService;

    private final PasswordEncoder passwordEncoder;

    private final JwtUtil jwtUtil;

    @Override
    public LoginVO login(LoginRequest request) {
        User user = userDbService.findActiveByUsername(request.getUsername());
        ThrowUtil.throwIf(user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword()),
                ErrorCode.LOGIN_FAILED);
        LoginVO vo = new LoginVO();
        // 角色与令牌版本随令牌下发：角色供菜单/鉴权，版本供失效校验
        UserRole role = UserRole.of(user.getRole());
        Integer tokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        vo.setToken(jwtUtil.sign(user.getId(), user.getUsername(), role, tokenVersion));
        vo.setRole(role.getCode());
        return vo;
    }

    @Override
    public void register(RegisterRequest request) {
        ThrowUtil.throwIf(userDbService.findActiveByUsername(request.getUsername()) != null,
                ErrorCode.USERNAME_EXISTS);
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setStatus(1);
        // 自助注册一律为普通用户，管理员由既有管理员在用户管理中调整
        user.setRole(UserRole.USER.getCode());
        userDbService.save(user);
    }
}
