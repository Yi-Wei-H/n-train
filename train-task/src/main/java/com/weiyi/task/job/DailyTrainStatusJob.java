package com.weiyi.task.job;

import com.weiyi.business.api.api.DailyTrainApi;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 发车状态巡检：每分钟推进到点车次状态（未发车→运行中→已到达，含跨天车次规则）。
 * 注意：Quartz 实例化不支持构造注入，必须用字段注入。
 */
@Slf4j
public class DailyTrainStatusJob extends TraceIdJob {

    @Autowired
    private DailyTrainApi dailyTrainApi;

    @Override
    protected void handle(JobExecutionContext context) {
        try {
            int updated = dailyTrainApi.sweepStatus();
            if (updated > 0) {
                log.info("发车状态巡检：本次推进 {} 行", updated);
            }
        } catch (Exception e) {
            log.warn("发车状态巡检失败: {}", e.getMessage());
        }
    }
}
