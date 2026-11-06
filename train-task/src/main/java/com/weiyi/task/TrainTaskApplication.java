package com.weiyi.task;

import com.weiyi.business.api.config.BusinessApiFeignConfig;
import com.weiyi.member.api.config.MemberApiFeignConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 定时任务服务：Quartz JDBC 持久化（QRTZ_ 表在 ntrain_task 库），
 * 任务不随启动自动注册，由超管在管理台"定时任务"页手动新增（白名单见 JobEnum）。
 * 任务经 Feign 触发 business / member 的巡检端点，本服务无业务表。
 */
@SpringBootApplication
@EnableFeignClients(basePackages = {"com.weiyi.business.api", "com.weiyi.member.api"},
        defaultConfiguration = {BusinessApiFeignConfig.class, MemberApiFeignConfig.class})
public class TrainTaskApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrainTaskApplication.class, args);
    }
}
