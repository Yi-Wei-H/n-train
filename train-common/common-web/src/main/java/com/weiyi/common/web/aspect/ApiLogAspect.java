package com.weiyi.common.web.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 接口访问日志：方法 + URI + 耗时 + 响应码，异常交由全局处理器统一记录。
 */
@Slf4j
@Aspect
public class ApiLogAspect {

    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = pjp.proceed();
            log.info("[api] {} {} cost={}ms code={}", httpMethod(), uri(), cost(start), resultCode(result));
            return result;
        } catch (Throwable e) {
            log.warn("[api] {} {} cost={}ms error={}", httpMethod(), uri(), cost(start), e.getMessage());
            throw e;
        }
    }

    private long cost(long start) {
        return System.currentTimeMillis() - start;
    }

    private String httpMethod() {
        HttpServletRequest request = currentRequest();
        return request == null ? "-" : request.getMethod();
    }

    private String uri() {
        HttpServletRequest request = currentRequest();
        return request == null ? "-" : request.getRequestURI();
    }

    private String resultCode(Object result) {
        if (result instanceof com.weiyi.common.core.result.Result<?> r) {
            return String.valueOf(r.getCode());
        }
        if (result instanceof ResponseEntity<?> entity) {
            return String.valueOf(entity.getStatusCode().value());
        }
        return result == null ? "null" : "ok";
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest();
        }
        return null;
    }
}
