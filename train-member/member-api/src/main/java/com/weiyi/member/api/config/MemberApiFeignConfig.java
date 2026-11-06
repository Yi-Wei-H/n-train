package com.weiyi.member.api.config;

import com.weiyi.member.api.trace.MemberApiTraceIdInterceptor;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

/**
 * MemberApi Feign 客户端默认配置（经 @EnableFeignClients(defaultConfiguration) 生效）。
 */
public class MemberApiFeignConfig {

    @Bean
    public ErrorDecoder dataApiErrorDecoder() {
        return new MemberApiErrorDecoder();
    }

    /** 出站请求带上当前线程的 traceId（MDC 为空则不带，由下游入口兜底生成） */
    @Bean
    public RequestInterceptor traceIdRequestInterceptor() {
        return new MemberApiTraceIdInterceptor();
    }
}
