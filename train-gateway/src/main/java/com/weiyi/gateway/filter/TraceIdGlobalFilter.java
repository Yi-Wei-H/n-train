package com.weiyi.gateway.filter;

import com.weiyi.common.core.constant.Constants;
import com.weiyi.common.core.trace.TraceIdSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 全链路 traceId 的发起端：请求进入网关即确保存在合法 X-Trace-Id（无/非法则生成），
 * 写回响应头供调用方报障，并以请求头形式传给下游服务（服务端 TraceIdFilter 透传同一个值）。
 *
 * <p>合法 = 恰好 16 位小写 hex；请求头里的原值一律先剥掉再注入解析后的值，
 * 上游伪造的非法值（空白、长度不符、大写、业务单号等）一律丢弃重生成。
 * 判定与服务入口 Filter 共用 {@link TraceIdSupport} 一套规则，两处标准一致，
 * 不会出现"网关放行、服务端重生成"导致同一请求前后 traceId 不同。
 *
 * <p>网关自身日志不要求携带 traceId（Reactor 线程模型下 MDC 不可靠，见 spec Simplifications 2），
 * 因此这里不碰 MDC。
 *
 * <p>排在 {@link AuthGlobalFilter}（order = -10）之前：鉴权拒绝的响应也带上 traceId。
 * 本过滤器任何异常都不改变请求走向，只 warn（规则 9）。
 */
@Slf4j
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String traceId;
        try {
            traceId = TraceIdSupport.resolve(request.getHeaders().getFirst(Constants.HEADER_TRACE_ID));
        } catch (Exception e) {
            log.warn("[trace] 网关生成 traceId 失败，本次按无标记继续: {}", e.getMessage());
            return chain.filter(exchange);
        }
        if (traceId == null) {
            return chain.filter(exchange);
        }
        try {
            ServerHttpResponse response = exchange.getResponse();
            response.getHeaders().set(Constants.HEADER_TRACE_ID, traceId);
            ServerHttpRequest mutated = request.mutate()
                    .headers(headers -> headers.set(Constants.HEADER_TRACE_ID, traceId))
                    .build();
            return chain.filter(exchange.mutate().request(mutated).build());
        } catch (Exception e) {
            log.warn("[trace] 网关注入 traceId 失败，本次按无标记继续: {}", e.getMessage());
            return chain.filter(exchange);
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
