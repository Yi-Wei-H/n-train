package com.weiyi.common.redis.service;

import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.redis.constant.RedisKeys;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

/**
 * 登录失败锁定：同一账号 10 分钟窗口内连续失败 ≥5 次即锁定，登录成功清零。
 * userType 区分会员(M)/管理员(A)账号空间。
 */
@RequiredArgsConstructor
public class LoginFailGuard {

    private static final int MAX_FAILS = 5;
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    private final RedisService redisService;

    /** 密码校验前调用：已锁定则拒绝（先于 BCrypt 计算，避免被锁账号继续消耗算力） */
    public void checkLocked(String userType, String username) {
        if (redisService.getCounter(RedisKeys.loginFailCount(userType, username)) >= MAX_FAILS) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }
    }

    /** 记一次失败；首次失败时设置 10 分钟窗口 */
    public void recordFail(String userType, String username) {
        redisService.increment(RedisKeys.loginFailCount(userType, username), LOCK_TTL);
    }

    /** 登录成功清零 */
    public void clear(String userType, String username) {
        redisService.delete(RedisKeys.loginFailCount(userType, username));
    }
}
