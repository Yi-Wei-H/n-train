package com.weiyi.agent.api.trace;

import com.weiyi.common.core.constant.Constants;
import com.weiyi.common.core.trace.TraceIdSupport;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;

/**
 * Feign 出站 traceId 拦截器：当前线程 MDC 有 traceId 就放进请求头，
 * 下游服务端入口（TraceIdFilter）读到同一个值，实现跨服务日志串联。
 *
 * <p>MDC 为空（非请求线程发起的调用）不设置请求头，由下游入口自行生成。
 * 拦截器不改变请求结果：自身异常只 warn 后照常放行。
 */
@Slf4j
public class AgentApiTraceIdInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        try {
            String traceId = TraceIdSupport.current();
            if (traceId != null && !traceId.isBlank()) {
                template.header(Constants.HEADER_TRACE_ID, traceId);
            }
        } catch (Exception e) {
            log.warn("[trace] Feign 出站 traceId 注入失败，本次调用不带标记: {}", e.getMessage());
        }
    }
}
