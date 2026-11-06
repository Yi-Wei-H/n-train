package com.weiyi.common.web.config;

import com.weiyi.common.web.advice.GlobalExceptionHandler;
import com.weiyi.common.web.aspect.ApiLogAspect;
import com.weiyi.common.web.filter.TraceIdFilter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;

/**
 * common-web 自动装配：traceId 过滤器 + 全局异常处理 + 接口日志切面 + Jackson 定制。
 */
@AutoConfiguration
public class WebAutoConfiguration {

    public static final int TRACE_FILTER_ORDER = -100;

    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration() {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>(new TraceIdFilter());
        registration.setOrder(TRACE_FILTER_ORDER);
        registration.addUrlPatterns("/*");
        registration.setName("traceIdFilter");
        return registration;
    }

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    public ApiLogAspect apiLogAspect() {
        return new ApiLogAspect();
    }
}
