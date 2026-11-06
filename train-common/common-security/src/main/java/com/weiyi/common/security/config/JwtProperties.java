package com.weiyi.common.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置。secret 来自环境变量 JWT_SECRET（secrets/.env），禁止默认值；
 * 有效期允许项目内默认。
 */
@Data
@ConfigurationProperties(prefix = "ntrain.jwt")
public class JwtProperties {

    /** HS256 密钥，≥32 字节 */
    private String secret;

    /** token 有效期（小时） */
    private int expireHours = 24;
}
