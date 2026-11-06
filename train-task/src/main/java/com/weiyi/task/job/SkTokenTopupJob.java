package com.weiyi.task.job;

import com.weiyi.business.api.api.SkTokenApi;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 令牌补偿：扫描令牌耗尽但仍有余票的车次，加量并覆盖缓存。
 * 注意：Quartz 实例化不支持构造注入，必须用字段注入。
 */
@Slf4j
public class SkTokenTopupJob extends TraceIdJob {

    @Autowired
    private SkTokenApi skTokenApi;

    @Override
    protected void handle(JobExecutionContext context) {
        try {
            int handled = skTokenApi.topup();
            if (handled > 0) {
                log.info("令牌补偿：{} 个车次已加量", handled);
            }
        } catch (Exception e) {
            log.warn("令牌补偿失败: {}", e.getMessage());
        }
    }
}
