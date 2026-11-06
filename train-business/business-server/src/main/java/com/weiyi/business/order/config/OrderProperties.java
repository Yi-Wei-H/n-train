package com.weiyi.business.order.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 订单域配置。支付时限以分钟表达（不再受 RocketMQ 固定延迟档位约束）：
 * 下单时用 MqConst.delayLevelCovering 取覆盖档位投递超时关单延迟消息，
 * 消费端按订单实际支付截止时间复判，未到期则 re-arm 补投接力。
 */
@Data
@ConfigurationProperties("ntrain.order")
public class OrderProperties {

    /** 支付时限（分钟），member 的 pay-timeout 与 H5 倒计时须与此对齐 */
    private int payTimeoutMinutes = 15;

    /** 下单总尝试次数上限（首试 + 重试）；仅瞬态异常（DataAccessException/锁竞争）重试 */
    private int createMaxAttempts = 2;

    /** 下单重试总预算；预算链约束：前端/网关 > member Feign readTimeout(≥5s) > 此值 > 单次锁等待 */
    private Duration createRetryBudget = Duration.ofSeconds(4);

    /** 下单车次锁单次等待上限（实际等待 = min(剩余预算, 此值)） */
    private Duration createLockWaitMax = Duration.ofMillis(3000);

    public Duration payTimeout() {
        return Duration.ofMinutes(payTimeoutMinutes);
    }
}
