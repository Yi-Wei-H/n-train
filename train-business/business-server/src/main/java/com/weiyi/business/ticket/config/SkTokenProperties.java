package com.weiyi.business.ticket.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties("ntrain.sk-token")
public class SkTokenProperties {

    /** 令牌大闸开关：false 时闸门直通（防超卖不受影响） */
    private boolean enabled = true;

    /** 初始发放系数：总发放量 = 座位数 × 站对数 × 系数 */
    private double multiplier = 1.2;

    /** 补偿任务单次加量（令牌耗尽但仍有余票时每次补多少） */
    private int topupStep = 100;
}
