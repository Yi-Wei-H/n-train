package com.weiyi.task.job;

import com.weiyi.business.api.api.OrderApi;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 关单兜底：RocketMQ 延迟消息为主
 * 超时未支付订单（应用重启丢消息等漏网场景），关单与座位释放由 business 本地事务完成。
 * 注意：Quartz 实例化不支持构造注入，必须用字段注入。
 */
@Slf4j
public class OrderCloseSweepJob extends TraceIdJob {

    @Autowired
    private OrderApi orderApi;

    @Override
    protected void handle(JobExecutionContext context) {
        try {
            int closed = orderApi.sweepTimeout();
            if (closed > 0) {
                log.info("关单兜底扫描：本次关单 {} 笔", closed);
            }
        } catch (Exception e) {
            log.warn("关单兜底任务异常: {}", e.getMessage());
        }
    }
}
