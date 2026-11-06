package com.weiyi.common.redis.ratelimit;

import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * 加载滑动窗口的lua脚本
 */
@Slf4j
public class SlidingWindowRateLimiter {

    private final String luaScript;

    public SlidingWindowRateLimiter() {
        try {
            this.luaScript = new String(new ClassPathResource("scripts/rate_limit_single.lua")
                    .getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("加载 rate_limit_single.lua 失败", e);
        }
    }

    /**
     * 底层通用方法
     * @param redissonClient redisson
     * @param keys redis KEYS数组，滑动窗口这里一般只有1个key
     * @param current 当前时间戳ms
     * @param interval 窗口大小ms
     * @param count 窗口最大允许次数
     * @return true放行，false拒绝
     */
    public boolean isAllowed(RedissonClient redissonClient, List<Object> keys, long current, long interval, int count) {
        return isAllowed(redissonClient, keys, current, interval, count, false);
    }

    /**
     * 底层通用方法（可指定严格模式）。
     * strict=true 时 Redis 异常直接抛限流异常（fail-closed），用于安全敏感接口；
     * 默认 fail-open 仅保留给非安全场景的降级放行。
     */
    public boolean isAllowed(RedissonClient redissonClient, List<Object> keys, long current, long interval,
                             int count, boolean strict) {
        Long ret;
        try {
            ret = redissonClient.getScript().eval(
                    RScript.Mode.READ_WRITE,
                    luaScript,
                    RScript.ReturnType.LONG,
                    keys,
                    current, interval, count
            );
        } catch (Exception e) {
            if (strict) {
                // 与正常限流区分开：依赖故障返回系统错误，避免被误读为触发限流
                log.error("滑动窗口限流调用redis异常（严格模式拒绝），key={}", keys, e);
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "服务暂时不可用，请稍后再试");
            }
            // Redis网络异常、超时等。这里策略按需调整：true=降级放行 / false=降级拒绝
            log.error("滑动窗口限流调用redis异常,key={}", keys, e);
            return true;
        }
        return ret != null && ret == 1L;
    }

    /**
     * 单key便捷调用
     */
    public Boolean useWindowRateLimiter(RedissonClient redissonClient, String key, long ts, long interval, int count) {
        return isAllowed(redissonClient, Collections.singletonList(key), ts, interval, count);
    }
}
