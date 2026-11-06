package com.weiyi.business;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 业务域服务：列车（车次/车站/余票/座位）+ 订单（下单/支付/退款/关单）两个子域同库本地事务。
 * 不消费任何外部 Feign 契约（锁座/释放已内部调用），仅对外提供 business-api 契约。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan({"com.weiyi.business.ticket.infra.mapper", "com.weiyi.business.order.infra.mapper"})
public class TrainBusinessApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrainBusinessApplication.class, args);
    }
}
