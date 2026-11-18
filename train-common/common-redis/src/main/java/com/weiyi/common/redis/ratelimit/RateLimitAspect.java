package com.weiyi.common.redis.ratelimit;

import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.spi.UserIdProvider;
import com.weiyi.common.redis.constant.RedisKeys;
import com.weiyi.common.redis.util.RequestIdentityUtil;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;

import java.lang.reflect.Method;
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

    /**
     * 切点不做参数绑定（同一方法叠加多个 @RateLimit 时编译器会包装成 @RateLimits 容器，
     * 带绑定的 @annotation 匹配不到容器，历史上导致限流静默失效），注解统一由
     * getAnnotationsByType 解析——单个直接存在与容器包装两种形态都正确。
     */
    @Around("@annotation(com.weiyi.common.redis.ratelimit.RateLimit) || "
            + "@annotation(com.weiyi.common.redis.ratelimit.RateLimits)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        RateLimit[] rules = method.getAnnotationsByType(RateLimit.class);
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
