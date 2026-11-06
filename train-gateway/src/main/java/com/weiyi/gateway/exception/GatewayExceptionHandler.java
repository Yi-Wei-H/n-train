package com.weiyi.gateway.exception;

import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.result.Result;
import com.weiyi.common.core.util.JacksonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * 网关统一异常出口：把网关侧抛出的异常包装成 {@link Result} JSON，与各业务服务返回体一致。
 * 业务异常返回 HTTP 200，由 code 区分错误；路由/下游/限流等按对应 HTTP 状态码返回；
 * 未知异常收口为 500 + ERROR（错误服务繁忙，请稍后重试），堆栈只进日志，不向前端泄露。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // 响应已提交（流式响应发了一半/客户端断开），状态码与包体都无法再改写
        if (exchange.getResponse().isCommitted()) {
            return Mono.error(ex);
        }

        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().name();
        String path = request.getURI().getPath();
        // 反应式链路中的异常可能被 ReactiveException/CompletionException 包装，先拆到真实原因再分类
        Throwable cause = Exceptions.unwrap(ex);

        HttpStatusCode status;
        Result<Void> body;

        if (cause instanceof BusinessException bex) {
            status = HttpStatus.OK;
            body = Result.fail(bex.getCode(), bex.getMessage());
            log.info("[网关业务异常] {} {} code={}, message={}", method, path, bex.getCode(), bex.getMessage());
        } else if (cause instanceof ResponseStatusException sex) {
            // 无匹配路由 404、下游无可用实例 503、限流 429、下游超时 504 等都属于这一类
            status = sex.getStatusCode();
            body = Result.fail(toErrorCode(status.value()));
            log.warn("[网关请求异常] {} {} status={}, reason={}", method, path, status.value(), sex.getReason());
        } else if (cause instanceof TimeoutException) {
            status = HttpStatus.GATEWAY_TIMEOUT;
            body = Result.fail(ErrorCode.REMOTE_ERROR);
            log.warn("[网关请求超时] {} {}", method, path);
        } else if (cause instanceof ConnectException) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            body = Result.fail(ErrorCode.REMOTE_ERROR);
            log.warn("[网关下游不可达] {} {} message={}", method, path, cause.getMessage());
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            body = Result.fail(ErrorCode.ERROR);
            log.error("[网关系统异常] {} {}", method, path, cause);
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(JacksonUtil.toJson(body).getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /** HTTP 状态码对齐项目错误码，前端既能按状态码走错误分支，也能读 body.code 统一处理 */
    private ErrorCode toErrorCode(int status) {
        return switch (status) {
            case 400 -> ErrorCode.PARAM_ERROR;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 429 -> ErrorCode.RATE_LIMITED;
            case 502, 503, 504 -> ErrorCode.REMOTE_ERROR;
            default -> ErrorCode.ERROR;
        };
    }
}
