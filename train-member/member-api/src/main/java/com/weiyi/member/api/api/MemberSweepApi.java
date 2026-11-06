package com.weiyi.member.api.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 会员域巡检接口（train-task 定时触发）：钱包侧对账兜底。
 */
@FeignClient(name = "train-member", contextId = "memberSweepApi")
public interface MemberSweepApi {

    /** 退款对账兜底：钱包已入账但订单域未确认的批次重试 confirm，返回补确认数 */
    @PostMapping("/mb/sweep/refund-confirm")
    int sweepRefundConfirms();
}
