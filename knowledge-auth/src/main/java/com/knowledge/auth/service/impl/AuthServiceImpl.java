package com.knowledge.auth.service.impl;

import com.knowledge.auth.db.UserDbService;
import com.knowledge.auth.service.AuthService;
import com.knowledge.auth.util.JwtUtil;
import com.knowledge.common.domain.entity.User;
import com.knowledge.common.dto.request.auth.LoginRequest;
import com.knowledge.common.dto.request.auth.RegisterRequest;
import com.knowledge.common.dto.response.auth.LoginVO;
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
        vo.setToken(jwtUtil.sign(user.getId(), user.getUsername()));
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
        userDbService.save(user);
    }
}
