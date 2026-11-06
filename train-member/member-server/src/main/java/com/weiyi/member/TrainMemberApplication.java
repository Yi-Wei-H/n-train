package com.weiyi.member;

import com.weiyi.agent.api.config.AgentApiFeignConfig;
import com.weiyi.business.api.config.BusinessApiFeignConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.weiyi.member.infra.mapper")
@EnableFeignClients(basePackages = {"com.weiyi.business.api", "com.weiyi.agent.api"},
        defaultConfiguration = {BusinessApiFeignConfig.class, AgentApiFeignConfig.class})
public class TrainMemberApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrainMemberApplication.class, args);
    }
}
