package com.knowledge.auth.controller;

import com.knowledge.auth.service.AuthService;
import com.knowledge.common.core.util.R;
import com.knowledge.common.dto.request.auth.LoginRequest;
import com.knowledge.common.dto.request.auth.RegisterRequest;
import com.knowledge.common.dto.response.auth.LoginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：登录与注册。
 *
 * <p>用户账号的查询与维护见 {@link UserController}。
 *
 * @author cxxl
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 登录 */
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return R.ok(authService.login(request));
    }

    /** 注册 */
    @PostMapping("/register")
    public R<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return R.ok(null, "注册成功");
    }
}
