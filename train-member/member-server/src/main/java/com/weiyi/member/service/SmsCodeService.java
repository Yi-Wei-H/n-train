package com.weiyi.member.service;

import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.common.core.util.DesensitizeUtil;
import com.weiyi.common.redis.constant.RedisKeys;
import com.weiyi.common.redis.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * 短信验证码登录（模拟真实业务）：6 位随机码存 Redis 5 分钟、验证即消费，60 秒发送冷却。
 * 发送前置图形验证码校验由调用方（MemberAuthService）完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsCodeService {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration COOLDOWN = Duration.ofSeconds(60);
    private final SecureRandom random = new SecureRandom();

    private final RedisService redisService;
    private final MemberUserService memberUserService;

    /** 生成并发送验证码，返回验证码明文（由调用方决定是否回显给前端） */
    public String sendCode(String phone) {
        BizAssert.isTrue(!redisService.hasKey(RedisKeys.smsCooldown(phone)), ErrorCode.SMS_SEND_FREQUENT);
        BizAssert.notNull(memberUserService.getAuthByPhone(phone), ErrorCode.ACCOUNT_NOT_EXISTS);
        String code = String.format("%06d", random.nextInt(1000000));
        redisService.set(RedisKeys.smsCode(phone), code, CODE_TTL);
        redisService.set(RedisKeys.smsCooldown(phone), "1", COOLDOWN);
        // 模拟短信下发；接入真实网关时替换此行（日志只打脱敏手机号，验证码开发期可见便于联调）
        log.info("短信验证码已发送 {}：{}", DesensitizeUtil.phone(phone), code);
        return code;
    }

    /** 验证并消费（一次一用） */
    public void verifyAndConsume(String phone, String code) {
        String saved = redisService.getAndDelete(RedisKeys.smsCode(phone), String.class);
        if (saved == null || !saved.equals(code)) {
            // 验证码不存在（过期/未发送）或输错：日志便于排查"登录一路正常但失败"的场景
            log.warn("短信验证码校验失败：phone={} 原因={}", DesensitizeUtil.phone(phone),
                    saved == null ? "验证码不存在（已过期或未发送）" : "验证码不匹配");
        }
        BizAssert.isTrue(saved != null && saved.equals(code), ErrorCode.SMS_CODE_INVALID);
    }

    /** 验证码是否仍有效（不消费，前端倒计时提示用可省） */
    public boolean pending(String phone) {
        return redisService.hasKey(RedisKeys.smsCode(phone));
    }
}
