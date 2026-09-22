package com.knowledge.auth.util;

import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import com.knowledge.common.security.KnowledgeUser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * JWT 令牌工具测试。
 *
 * @author cxxl
 */
class JwtUtilTest {

    private static final String SECRET = "test-secret-0123456789-abcdefghijklmnopqrstuvwxyz";

    @Test
    void signAndParseShouldRoundTrip() {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 7);

        String token = jwtUtil.sign(100L, "admin");
        KnowledgeUser user = jwtUtil.parse(token);

        assertEquals(100L, user.getId());
        assertEquals("admin", user.getUsername());
    }

    @Test
    void parseShouldThrowWhenTokenTampered() {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 7);
        String token = jwtUtil.sign(100L, "admin");

        KnowledgeException e = assertThrows(KnowledgeException.class,
                () -> jwtUtil.parse(token.substring(0, token.length() - 3) + "abc"));

        assertEquals(ErrorCode.UNAUTHORIZED, e.getErrorCode());
    }

    @Test
    void parseShouldThrowWhenTokenExpired() {
        JwtUtil jwtUtil = new JwtUtil(SECRET, 0);
        String token = jwtUtil.sign(100L, "admin");

        KnowledgeException e = assertThrows(KnowledgeException.class, () -> jwtUtil.parse(token));

        assertEquals(ErrorCode.UNAUTHORIZED, e.getErrorCode());
    }
}
