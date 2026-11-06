package com.weiyi.common.security.util;

import com.weiyi.common.core.constant.Constants;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.security.config.JwtProperties;
import com.weiyi.common.security.context.LoginUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与校验（HS256）。
 * 网关（reactive，不依赖本模块）与网关后的服务必须共用同一个 JWT_SECRET。
 */
@Slf4j
public class JwtUtil {

    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_NAME = "name";
    public static final String CLAIM_SUPER = "super";

    private final SecretKey key;
    private final long expireMillis;

    public JwtUtil(JwtProperties properties) {
        byte[] bytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET 长度必须 ≥32 字节（当前 " + bytes.length + "）");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.expireMillis = properties.getExpireHours() * 3600_000L;
    }

    public String create(long userId, String userType, String username) {
        return create(userId, userType, username, false);
    }

    /** 管理端登录时携带超管标识，供 task 等无权限库的服务做强校验 */
    public String create(long userId, String userType, String username, boolean superAdmin) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TYPE, userType)
                .claim(CLAIM_NAME, username)
                .claim(CLAIM_SUPER, superAdmin)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 校验签名与有效期，成功返回登录用户；失败抛 BusinessException(TOKEN_EXPIRED/UNAUTHORIZED)。
     */
    public LoginUser verify(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).build()
                    .parseSignedClaims(token).getPayload();
            return new LoginUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_TYPE, String.class),
                    claims.get(CLAIM_NAME, String.class),
                    Boolean.TRUE.equals(claims.get(CLAIM_SUPER, Boolean.class)));
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("invalid jwt: {}", e.getMessage());
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    /** 快速判断 token 归属端，供网关路由约束使用 */
    public static boolean isAdminType(LoginUser user) {
        return user != null && Constants.USER_TYPE_ADMIN.equals(user.getUserType());
    }
}
