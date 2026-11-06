package com.weiyi.common.web.filter;

import com.weiyi.common.core.constant.Constants;
import com.weiyi.common.core.trace.TraceIdSupport;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * traceId：优先透传网关/上游的 X-Trace-Id，否则生成；写 MDC 与响应头。
 * 直连绕过网关时的兜底（规则 3）；"合法"的判定与网关共用 {@link TraceIdSupport} 一套规则，
 * 非法值（长度不符/非小写 hex/业务单号等）在此同样被丢弃重生成，不会透传到 MDC 与响应头。
 */
@Slf4j
public class TraceIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = TraceIdSupport.resolve(request.getHeader(Constants.HEADER_TRACE_ID));
        TraceIdSupport.bind(traceId);
        if (traceId != null) {
            response.setHeader(Constants.HEADER_TRACE_ID, traceId);
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            TraceIdSupport.clear();
        }
    }
}
