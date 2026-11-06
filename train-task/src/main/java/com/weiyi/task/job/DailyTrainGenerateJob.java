package com.weiyi.task.job;

import com.weiyi.business.api.api.DailyTrainApi;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDate;

/**
 * 每日车次生成：每天凌晨生成未来第 N 天（幂等，重复生成自动跳过已有）。
 * 注意：Quartz 实例化不支持构造注入，必须用字段注入。
 */
@Slf4j
public class DailyTrainGenerateJob extends TraceIdJob {

    @Autowired
    private DailyTrainApi dailyTrainApi;

    @Value("${ntrain.task.generate-days:15}")
    private int days;

    @Override
    protected void handle(JobExecutionContext context) {
        LocalDate rideDate = LocalDate.now().plusDays(days);
        try {
            var result = dailyTrainApi.generateForDate(rideDate);
            log.info("每日车次生成 {}：车次 {} 厢 {} 座 {} 余票行 {}",
                    rideDate, result.getTrainCount(), result.getCarriageCount(),
                    result.getSeatCount(), result.getTicketCount());
        } catch (Exception e) {
            log.warn("每日车次生成失败 {}: {}", rideDate, e.getMessage());
        }
    }
}
