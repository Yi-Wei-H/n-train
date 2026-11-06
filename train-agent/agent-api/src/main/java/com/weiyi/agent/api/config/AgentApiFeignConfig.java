package com.weiyi.agent.api.config;

import com.weiyi.agent.api.trace.AgentApiTraceIdInterceptor;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

/**
 * AgentApi Feign 客户端默认配置（经 @EnableFeignClients(defaultConfiguration) 生效）。
 */
public class AgentApiFeignConfig {

    /**
     * 方法名与 business/member 的 Feign 配置保持一致：Feign 子上下文里同类型 Bean 若同名则相互覆盖
     * （只留一个），不同名会并存两个 ErrorDecoder，取 Bean 时抛 NoUniqueBeanDefinitionException
     * 并被 Feign 吞掉退化成默认解码器——所有客户端的错误码透传都会失效。
     */
    @Bean
    public ErrorDecoder dataApiErrorDecoder() {
        return new AgentApiErrorDecoder();
    }

    /** 出站请求带上当前线程的 traceId（MDC 为空则不带，由下游入口兜底生成） */
    @Bean
    public RequestInterceptor traceIdRequestInterceptor() {
        return new AgentApiTraceIdInterceptor();
    }
}
