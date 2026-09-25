package com.knowledge.auth.service.impl;

import com.knowledge.auth.db.UserDbService;
import com.knowledge.auth.util.JwtUtil;
import com.knowledge.common.domain.entity.User;
import com.knowledge.common.dto.request.auth.LoginRequest;
import com.knowledge.common.dto.request.auth.RegisterRequest;
import com.knowledge.common.dto.response.auth.LoginVO;
import com.knowledge.common.enums.user.UserRole;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证服务测试。
 *
 * @author cxxl
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserDbService userDbService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void loginShouldReturnTokenWhenCredentialsMatch() {
        when(userDbService.findActiveByUsername("admin")).thenReturn(adminUser());
        when(passwordEncoder.matches("pass123456", "encoded")).thenReturn(true);
        when(jwtUtil.sign(1L, "admin", UserRole.ADMIN, 0)).thenReturn("jwt-token");

        LoginVO vo = authService.login(loginRequest("admin", "pass123456"));

        assertEquals("jwt-token", vo.getToken());
        assertEquals(UserRole.ADMIN.getCode(), vo.getRole());
    }

    @Test
    void loginShouldFallbackToUserRoleWhenRoleMissing() {
        User plain = adminUser();
        plain.setRole(null);
        when(userDbService.findActiveByUsername("admin")).thenReturn(plain);
        when(passwordEncoder.matches("pass123456", "encoded")).thenReturn(true);
        when(jwtUtil.sign(1L, "admin", UserRole.USER, 0)).thenReturn("jwt-token");

        LoginVO vo = authService.login(loginRequest("admin", "pass123456"));

        assertEquals(UserRole.USER.getCode(), vo.getRole());
    }

    @Test
    void loginShouldCarryCurrentTokenVersion() {
        User user = adminUser();
        user.setTokenVersion(5);
        when(userDbService.findActiveByUsername("admin")).thenReturn(user);
        when(passwordEncoder.matches("pass123456", "encoded")).thenReturn(true);
        when(jwtUtil.sign(1L, "admin", UserRole.ADMIN, 5)).thenReturn("jwt-token");

        LoginVO vo = authService.login(loginRequest("admin", "pass123456"));

        assertEquals("jwt-token", vo.getToken());
    }

    @Test
    void loginShouldThrowWhenUserNotFound() {
        when(userDbService.findActiveByUsername("nobody")).thenReturn(null);

        KnowledgeException e = assertThrows(KnowledgeException.class,
                () -> authService.login(loginRequest("nobody", "pass123456")));

        assertEquals(ErrorCode.LOGIN_FAILED, e.getErrorCode());
    }

    @Test
    void loginShouldThrowWhenPasswordMismatch() {
        when(userDbService.findActiveByUsername("admin")).thenReturn(adminUser());
        when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

        KnowledgeException e = assertThrows(KnowledgeException.class,
                () -> authService.login(loginRequest("admin", "wrong")));

        assertEquals(ErrorCode.LOGIN_FAILED, e.getErrorCode());
    }

    @Test
    void registerShouldThrowWhenUsernameExists() {
        when(userDbService.findActiveByUsername("admin")).thenReturn(adminUser());

        KnowledgeException e = assertThrows(KnowledgeException.class,
                () -> authService.register(registerRequest()));

        assertEquals(ErrorCode.USERNAME_EXISTS, e.getErrorCode());
        verify(userDbService, never()).save(any());
    }

    @Test
    void registerShouldEncodePasswordAndSave() {
        when(userDbService.findActiveByUsername("admin")).thenReturn(null);
        when(passwordEncoder.encode("pass123456")).thenReturn("encoded");

        authService.register(registerRequest());

        verify(passwordEncoder).encode("pass123456");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userDbService).save(captor.capture());
        // 自助注册一律普通用户，不得自行获得管理员
        assertEquals(UserRole.USER.getCode(), captor.getValue().getRole());
    }

    private LoginRequest loginRequest(String username, String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername(username);
        request.setPassword(password);
        return request;
    }

    private RegisterRequest registerRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("admin");
        request.setPassword("pass123456");
        return request;
    }

    private User adminUser() {
        User user = new User();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword("encoded");
        user.setStatus(1);
        user.setRole(UserRole.ADMIN.getCode());
        return user;
    }
}
