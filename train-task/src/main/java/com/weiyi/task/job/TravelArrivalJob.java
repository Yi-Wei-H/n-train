package com.weiyi.task.job;

import com.weiyi.business.api.api.OrderApi;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 下车过站巡检：车到达乘客下车站 → 已出行订单转已完成。
 * 注意：Quartz 实例化不支持构造注入，必须用字段注入。
 */
@Slf4j
public class TravelArrivalJob extends TraceIdJob {

    @Autowired
    private OrderApi orderApi;

    @Override
    protected void handle(JobExecutionContext context) {
        try {
            int advanced = orderApi.sweepArrival();
            if (advanced > 0) {
                log.info("下车推进：本次转已完成 {} 笔订单", advanced);
            }
        } catch (Exception e) {
            log.warn("下车过站巡检失败: {}", e.getMessage());
        }
    }
}
