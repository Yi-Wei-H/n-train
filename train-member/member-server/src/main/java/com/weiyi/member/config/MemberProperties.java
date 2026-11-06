package com.weiyi.member.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * member 业务参数（Nacos 可覆盖）：验证码触发阈值、充值档位。
 * 支付时限的权威配置在 business（ntrain.order.pay-timeout-minutes），H5 倒计时与其对齐。
 */
@Data
@ConfigurationProperties(prefix = "ntrain.member")
public class MemberProperties {

    /** 滑动窗口（秒）内下单次数超过阈值 → 下次下单需图形验证码 */
    private int captchaWindowSeconds = 60;
    private int captchaThreshold = 3;

    /** 验证码缓存时长（分钟） */
    private int captchaCodeCache = 1;

    /** 模拟短信：把短信验证码回显到发送接口响应（接入真实短信网关后关闭） */
    private boolean mockSmsEcho = true;

    /** 充值档位（元），即充即到账，余额上限 1000 由数据端校验 */
    private List<BigDecimal> rechargeTiers = List.of(
            BigDecimal.TEN, BigDecimal.valueOf(30), BigDecimal.valueOf(50),
            BigDecimal.valueOf(100), BigDecimal.valueOf(200), BigDecimal.valueOf(500));

    /** 余票查询缓存时长（秒，逻辑过期：过期后返回旧值并异步重建） */
    private int ticketCacheSeconds = 60;

    /** 余票查询缓存物理 TTL（秒，兜底防泄漏；逻辑过期前 key 不会自然消失） */
    private int ticketCachePhysicalSeconds = 600;
}
