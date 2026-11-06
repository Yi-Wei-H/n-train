package com.weiyi.task.job;

import com.weiyi.business.api.api.OrderApi;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 上车过站巡检：车发离乘客上车站（近似已上车）→ 订单/明细/车票转已出行。
 * 注意：Quartz 实例化不支持构造注入，必须用字段注入。
 */
@Slf4j
public class TravelBoardingJob extends TraceIdJob {

    @Autowired
    private OrderApi orderApi;

    @Override
    protected void handle(JobExecutionContext context) {
        try {
            int advanced = orderApi.sweepBoarding();
            if (advanced > 0) {
                log.info("上车推进：本次转已出行 {} 笔订单", advanced);
            }
        } catch (Exception e) {
            log.warn("上车过站巡检失败: {}", e.getMessage());
        }
    }
}
