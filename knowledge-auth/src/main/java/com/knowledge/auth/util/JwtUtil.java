package com.knowledge.auth.util;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTPayload;
import cn.hutool.jwt.JWTUtil;
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
     * @param userId   用户 ID（写入 subject）
     * @param username 用户名（写入载荷）
     * @return 令牌串
     */
    public String sign(Long userId, String username) {
        return JWT.create()
                .setSubject(String.valueOf(userId))
                .setPayload(CLAIM_USERNAME, username)
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
            return user;
        } catch (KnowledgeException e) {
            throw e;
        } catch (Exception e) {
            throw new KnowledgeException(ErrorCode.UNAUTHORIZED);
        }
    }
}
