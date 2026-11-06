package com.weiyi.task.job;

import com.weiyi.member.api.api.MemberSweepApi;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 退款对账兜底：主路径是支付/退款同步确认，本任务扫描
 * 钱包已入账但订单域未确认的批次重试 confirm（member 编排）。
 * 注意：Quartz 实例化不支持构造注入，必须用字段注入。
 */
@Slf4j
public class RefundConfirmSweepJob extends TraceIdJob {

    @Autowired
    private MemberSweepApi memberSweepApi;

    @Override
    protected void handle(JobExecutionContext context) {
        try {
            int confirmed = memberSweepApi.sweepRefundConfirms();
            if (confirmed > 0) {
                log.info("退款对账兜底：本次补确认 {} 个批次", confirmed);
            }
        } catch (Exception e) {
            log.warn("退款对账兜底异常: {}", e.getMessage());
        }
    }
}
