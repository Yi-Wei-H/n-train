package com.weiyi.common.redis.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流注解（可重复，多维叠加），滑动窗口计数，基于 Redis Lua 原子执行。
 * 例：@RateLimit(dimension = IP, count = 100, interval = 1)
 *    @RateLimit(dimension = USER, count = 5, interval = 1)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(RateLimits.class)
public @interface RateLimit {

    /** 限流维度 */
    LimitDimension dimension() default LimitDimension.IP;

    /** 窗口内最大次数 */
    int count();

    /** 窗口长度（秒） */
    int interval();

    /** 触发限流的提示文案 */
    String message() default "请求过于频繁，请稍后再试";

    /**
     * 严格模式：Redis/Lua 异常时拒绝请求（fail-closed）。
     * 默认 false 沿用降级放行；AI 等安全敏感接口必须置 true，不能静默放行。
     */
    boolean strict() default false;
}
