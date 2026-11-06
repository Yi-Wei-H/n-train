package com.weiyi.common.redis.config;

import com.weiyi.common.redis.ratelimit.SlidingWindowRateLimiter;
import org.redisson.api.RedissonClient;
import org.redisson.codec.TypedJsonJacksonCodec;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.weiyi.common.redis.ratelimit.RateLimitAspect;
import com.weiyi.common.redis.service.LoginFailGuard;
import com.weiyi.common.redis.service.RedisService;
import com.weiyi.common.redis.service.RsaKeyService;

/**
 * common-redis 自动装配。Redisson 连接参数来自 spring.data.redis.*（redisson-spring-boot-starter 桥接）。
 */
@AutoConfiguration
@ConditionalOnClass(RedissonClient.class)
public class CommonRedisAutoConfiguration {

    /** 统一 JSON 编解码（Jackson 2 codec mapper：Java 8 时间、无默认类型信息；类型化读取由 RedisService 处理） */
    @Bean
    public org.redisson.spring.starter.RedissonAutoConfigurationCustomizer ntrainRedissonCustomizer() {
        return config -> config.setCodec(new TypedJsonJacksonCodec(Object.class, CodecMappers.CODEC));
    }

    @Bean
    @ConditionalOnMissingBean
    public SlidingWindowRateLimiter slidingWindowRateLimiter() {
        return new SlidingWindowRateLimiter();
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisService redisService(RedissonClient redissonClient, SlidingWindowRateLimiter slidingWindowRateLimiter) {
        return new RedisService(redissonClient, slidingWindowRateLimiter);
    }

    @Bean
    @ConditionalOnMissingBean
    public RsaKeyService rsaKeyService(RedisService redisService) {
        return new RsaKeyService(redisService);
    }

    @Bean
    @ConditionalOnMissingBean
    public LoginFailGuard loginFailGuard(RedisService redisService) {
        return new LoginFailGuard(redisService);
    }

    @Bean
    public RateLimitAspect rateLimitAspect(RedissonClient redissonClient,
                                           org.springframework.beans.factory.ObjectProvider<com.weiyi.common.core.spi.UserIdProvider> userIdProvider) {
        return new RateLimitAspect(redissonClient, userIdProvider);
    }
}
