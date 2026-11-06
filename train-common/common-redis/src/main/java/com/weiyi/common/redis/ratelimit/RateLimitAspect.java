package com.weiyi.common.redis.ratelimit;

import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.spi.UserIdProvider;
import com.weiyi.common.redis.constant.RedisKeys;
import com.weiyi.common.redis.util.RequestIdentityUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.Collections;

/**
 * 限流切面：ZSET 滑动窗口（Lua 原子），支持 GLOBAL/IP/USER 多维叠加。
 */
@Slf4j
@Aspect
public class RateLimitAspect {

    private final RedissonClient redissonClient;
    private final ObjectProvider<UserIdProvider> userIdProvider;
    private final SlidingWindowRateLimiter slidingWindowRateLimiter = new SlidingWindowRateLimiter();

    public RateLimitAspect(RedissonClient redissonClient,
                           ObjectProvider<UserIdProvider> userIdProvider) {
        this.redissonClient = redissonClient;
        this.userIdProvider = userIdProvider;
    }

    @Around("@annotation(rateLimit) || @annotation(rateLimits)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit, RateLimits rateLimits) throws Throwable {
        RateLimit[] rules;
        if (rateLimit != null) {
            rules = new RateLimit[]{rateLimit};
        } else if (rateLimits != null) {
            rules = rateLimits.value();
        } else {
            return pjp.proceed();
        }
        for (RateLimit rule : rules) {
            check(rule, pjp);
        }
        return pjp.proceed();
    }

    private void check(RateLimit rule, ProceedingJoinPoint pjp) {
        String dimValue = switch (rule.dimension()) {
            case GLOBAL -> "global";
            case IP -> RequestIdentityUtil.currentIp();
            case DEVICE -> {
                String deviceId = RequestIdentityUtil.currentDeviceId();
                yield deviceId != null ? deviceId : RequestIdentityUtil.currentIp();
            }
            case USER -> {
                UserIdProvider provider = userIdProvider.getIfAvailable();
                Long uid = provider == null ? null : provider.getUserId();
                yield uid == null ? "anon" : String.valueOf(uid);
            }
        };
        String method = pjp.getSignature().toShortString();
        String key = RedisKeys.rateLimit(method, rule.dimension().name(), dimValue);
        boolean allowed = slidingWindowRateLimiter.isAllowed(redissonClient, Collections.singletonList(key),
                System.currentTimeMillis(), rule.interval() * 1000L, rule.count(), rule.strict());
        if (!allowed) {
            log.warn("rate limited: {} [{}]", key, rule.count() + "/" + rule.interval() + "s");
            throw new BusinessException(com.weiyi.common.core.exception.ErrorCode.RATE_LIMITED, rule.message());
        }
    }
}
