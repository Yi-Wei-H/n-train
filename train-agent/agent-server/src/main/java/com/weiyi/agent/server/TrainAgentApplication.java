package com.weiyi.agent.server;

import com.weiyi.business.api.config.BusinessApiFeignConfig;
import com.weiyi.member.api.config.MemberApiFeignConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("com.weiyi.agent.server.mapper")
@ConfigurationPropertiesScan
@SpringBootApplication
@EnableScheduling
@EnableFeignClients(basePackages = {"com.weiyi.business.api.api", "com.weiyi.member.api.api"},
        defaultConfiguration = {BusinessApiFeignConfig.class, MemberApiFeignConfig.class})
public class TrainAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrainAgentApplication.class, args);
    }
}
