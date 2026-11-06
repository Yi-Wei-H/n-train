package com.weiyi.common.security.config;

import com.weiyi.common.core.spi.UserIdProvider;
import com.weiyi.common.security.auth.PermissionResolver;
import com.weiyi.common.security.context.LoginContext;
import com.weiyi.common.security.interceptor.LoginContextInterceptor;
import com.weiyi.common.security.interceptor.PermissionInterceptor;
import com.weiyi.common.security.util.JwtUtil;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * common-security 自动装配：JwtUtil + 登录上下文/权限拦截器 + 限流用户维度 SPI。
 */
@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityAutoConfiguration {

    /** 只在配置了密钥的服务装配：签发/校验 token 的服务（member/admin）才有 ntrain.jwt.secret；
     *  只读网关注入请求头的服务（如 train-task）不配此属性，不创建 JwtUtil */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty("ntrain.jwt.secret")
    public JwtUtil jwtUtil(JwtProperties properties) {
        return new JwtUtil(properties);
    }

    /** USER 维度限流的用户来源 */
    @Bean
    @ConditionalOnMissingBean(UserIdProvider.class)
    public UserIdProvider securityUserIdProvider() {
        return LoginContext::getUserId;
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public WebMvcConfigurer securityInterceptorConfigurer(ObjectProvider<PermissionResolver> resolverProvider) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new LoginContextInterceptor()).addPathPatterns("/**").order(0);
                PermissionResolver resolver = resolverProvider.getIfAvailable();
                if (resolver != null) {
                    registry.addInterceptor(new PermissionInterceptor(resolver)).addPathPatterns("/**").order(1);
                }
            }
        };
    }
}
