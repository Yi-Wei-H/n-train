package com.weiyi.business.api.config;

import com.weiyi.business.api.trace.BusinessApiTraceIdInterceptor;
import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

/**
 * business-api Feign 客户端默认配置（经 @EnableFeignClients(defaultConfiguration) 生效）。
 */
public class BusinessApiFeignConfig {

    @Bean
    public ErrorDecoder dataApiErrorDecoder() {
        return new BusinessApiErrorDecoder();
    }

    /** 出站请求带上当前线程的 traceId（MDC 为空则不带，由下游入口兜底生成） */
    @Bean
    public RequestInterceptor traceIdRequestInterceptor() {
        return new BusinessApiTraceIdInterceptor();
    }
}
