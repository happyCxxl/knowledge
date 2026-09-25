package com.knowledge.auth.util;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTPayload;
import cn.hutool.jwt.JWTUtil;
import com.knowledge.common.enums.user.UserRole;
import com.knowledge.common.error.ErrorCode;
import com.knowledge.common.exception.KnowledgeException;
import com.knowledge.common.security.KnowledgeUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 令牌工具：签发与解析。
 *
 * @author cxxl
 */
@Component
public class JwtUtil {

    /** 用户名载荷键 */
    private static final String CLAIM_USERNAME = "username";

    /** 角色载荷键 */
    private static final String CLAIM_ROLE = "role";

    /** 令牌版本载荷键 */
    private static final String CLAIM_TOKEN_VERSION = "tv";

    private final byte[] secret;

    private final long ttlMillis;

    public JwtUtil(@Value("${knowledge.auth.jwt.secret}") String secret,
                   @Value("${knowledge.auth.jwt.ttl-days:7}") long ttlDays) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlMillis = ttlDays * 24 * 60 * 60 * 1000L;
    }

    /**
     * 签发令牌。
     *
     * @param userId       用户 ID（写入 subject）
     * @param username     用户名（写入载荷）
     * @param role         角色（写入载荷，供鉴权判定）
     * @param tokenVersion 令牌版本（写入载荷，供失效校验）
     * @return 令牌串
     */
    public String sign(Long userId, String username, UserRole role, Integer tokenVersion) {
        return JWT.create()
                .setSubject(String.valueOf(userId))
                .setPayload(CLAIM_USERNAME, username)
                .setPayload(CLAIM_ROLE, role.getCode())
                .setPayload(CLAIM_TOKEN_VERSION, tokenVersion == null ? 0 : tokenVersion)
                .setExpiresAt(new Date(System.currentTimeMillis() + ttlMillis))
                .setKey(secret)
                .sign();
    }

    /**
     * 解析令牌。
     *
     * @param token 令牌串
     * @return 用户模型
     * @throws KnowledgeException 签名无效或已过期（UNAUTHORIZED）
     */
    public KnowledgeUser parse(String token) {
        try {
            JWT jwt = JWT.of(token);
            if (!JWTUtil.verify(token, secret)) {
                throw new KnowledgeException(ErrorCode.UNAUTHORIZED);
            }
            Date expiresAt = jwt.getPayloads().getDate(JWTPayload.EXPIRES_AT);
            if (expiresAt == null || expiresAt.before(new Date())) {
                throw new KnowledgeException(ErrorCode.UNAUTHORIZED);
            }
            KnowledgeUser user = new KnowledgeUser();
            user.setId(Long.valueOf(jwt.getPayload("sub").toString()));
            user.setUsername(jwt.getPayload(CLAIM_USERNAME).toString());
            // 旧令牌不含角色载荷：回落 USER，避免历史令牌直接提权
            Object role = jwt.getPayload(CLAIM_ROLE);
            user.setRole(UserRole.of(role == null ? null : role.toString()));
            user.setTokenVersion(readTokenVersion(jwt));
            return user;
        } catch (KnowledgeException e) {
            throw e;
        } catch (Exception e) {
            throw new KnowledgeException(ErrorCode.UNAUTHORIZED);
        }
    }

    /** 读取令牌版本：缺失或非数字按 0 处理（与库表默认值一致） */
    private Integer readTokenVersion(JWT jwt) {
        Object value = jwt.getPayload(CLAIM_TOKEN_VERSION);
        if (value == null) {
            return 0;
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
