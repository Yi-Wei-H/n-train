package com.weiyi.gateway.util;

import com.weiyi.gateway.dto.TokenUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * 网关侧 JWT 校验（与 member/admin 的 common-security 共用同一 JWT_SECRET）。
 */
@Component
public class TokenVerifier {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_NAME = "name";
    private static final String CLAIM_SUPER = "super";

    private final SecretKey key;

    public TokenVerifier(@Value("${ntrain.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET 长度必须 ≥32 字节");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    /**
     * 校验签名与有效期；失败抛 io.jsonwebtoken 异常，由过滤器统一转 401。
     */
    public TokenUser verify(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        return new TokenUser(
                Long.parseLong(claims.getSubject()),
                claims.get(CLAIM_TYPE, String.class),
                claims.get(CLAIM_NAME, String.class),
                Boolean.TRUE.equals(claims.get(CLAIM_SUPER, Boolean.class)));
    }
}
