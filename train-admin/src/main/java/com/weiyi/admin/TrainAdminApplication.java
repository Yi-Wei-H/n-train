package com.weiyi.admin;

import com.weiyi.agent.api.config.AgentApiFeignConfig;
import com.weiyi.member.api.config.MemberApiFeignConfig;
import com.weiyi.business.api.config.BusinessApiFeignConfig;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.weiyi.admin.infra.mapper")
@EnableFeignClients(basePackages = {"com.weiyi.business.api", "com.weiyi.member.api", "com.weiyi.agent.api"},
        defaultConfiguration = {BusinessApiFeignConfig.class, MemberApiFeignConfig.class, AgentApiFeignConfig.class})
public class TrainAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrainAdminApplication.class, args);
    }
}
