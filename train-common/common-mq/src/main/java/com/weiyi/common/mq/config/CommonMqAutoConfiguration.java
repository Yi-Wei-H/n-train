package com.weiyi.common.mq.config;

import com.weiyi.common.mq.core.MqSender;
import com.weiyi.common.mq.core.MqTxListener;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * MQ 装配：服务配置了 rocketmq.name-server + rocketmq.producer.group 即生效；
 * 只消费不发送的服务可不配 producer.group（MqSender 仍会创建，发送时才报错）。
 *
 * <p>必须排在 RocketMQ 自动装配之后：@ConditionalOnBean(RocketMQTemplate) 只能感知
 * 已注册的 bean 定义，若先于 RocketMQAutoConfiguration 求值会静默跳过 MqSender 创建。
 */
@AutoConfiguration
@AutoConfigureAfter(RocketMQAutoConfiguration.class)
@ConditionalOnClass(RocketMQTemplate.class)
public class CommonMqAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MqTxListener mqTxListener() {
        return new MqTxListener();
    }

    @Bean
    @ConditionalOnBean(RocketMQTemplate.class)
    @ConditionalOnMissingBean
    public MqSender mqSender(RocketMQTemplate template, MqTxListener txListener) {
        return new MqSender(template, txListener);
    }
}
