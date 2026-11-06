package com.weiyi.common.web.advice;

import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.List;
import java.util.Optional;

/**
 * 全局异常出口：所有异常统一包装为 Result JSON，口径与网关一致。
 * 浏览器端点（经网关转发）业务异常与参数校验一律 HTTP 200，错误只体现在 body 的 code；
 * 服务间 RPC 端点返回裸 DTO，必须按错误码映射非 2xx（消费端 ErrorDecoder 依赖非 2xx 还原业务码）；
 * 框架异常保留自身状态码；未知异常收口为 500 + ERROR，堆栈只进日志，不向前端泄露。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 服务间 RPC 路径前缀（与各 *-api 契约的 @FeignClient 路径对应）：
     * /mb、/rpc 为 member，/od、/tk 为 business，/ag 为 agent。
     */
    private static final List<String> RPC_PATH_PREFIXES = List.of("/mb/", "/rpc/", "/od/", "/tk/", "/ag/");

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e, HttpServletRequest request) {
        Result<Void> body = Result.fail(e.getCode(), e.getMessage());
        return isRpcRequest(request)
                ? ResponseEntity.status(toHttpStatus(e.getCode())).body(body)
                : ResponseEntity.ok(body);
    }

    /**
     * 异步请求（SSE/DeferredResult）超时由容器在已提交的流式响应上回调，
     * 此时不能再写 Result 包体——返回 void 仅收口日志，避免“unexpected error”与转换器告警。
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncTimeout(AsyncRequestTimeoutException e) {
        log.debug("async request timeout: {}", e.getMessage());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> handleBind(BindException e, HttpServletRequest request) {
        String message = Optional.ofNullable(e.getBindingResult().getFieldError())
                .map(FieldError::getDefaultMessage)
                .filter(m -> !m.isBlank())
                .orElse(ErrorCode.PARAM_ERROR.getMessage());
        return paramError(request, message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<Void>> handleMethodValidation(HandlerMethodValidationException e,
                                                               HttpServletRequest request) {
        return paramError(request, firstMessage(e.getAllErrors()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraint(ConstraintViolationException e, HttpServletRequest request) {
        String message = e.getConstraintViolations().stream()
                .findFirst()
                .map(v -> v.getMessage())
                .filter(m -> !m.isBlank())
                .orElse(ErrorCode.PARAM_ERROR.getMessage());
        return paramError(request, message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception e, HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        // 框架异常：无匹配资源 404、方法不支持 405、媒体类型 415、ResponseStatusException 等，保留自身状态码
        if (e instanceof ErrorResponse errorResponse) {
            int status = errorResponse.getStatusCode().value();
            if (status >= 500) {
                // 5xx 需要保留堆栈（ApiLogAspect 只记录 message），4xx 属预期错误不重复打日志
                log.error("[请求异常] {} {} status={}", method, uri, status, e);
            }
            return new ResponseEntity<>(Result.fail(toErrorCode(status)),
                    errorResponse.getHeaders(), errorResponse.getStatusCode());
        }
        log.error("[系统异常] {} {}", method, uri, e);
        return ResponseEntity.internalServerError().body(Result.fail(ErrorCode.ERROR));
    }

    /** 参数校验：浏览器端点回 200（前端按 body.code 提示），RPC 端点回 400（消费端 ErrorDecoder 依赖非 2xx） */
    private ResponseEntity<Result<Void>> paramError(HttpServletRequest request, String message) {
        Result<Void> body = Result.fail(ErrorCode.PARAM_ERROR, message);
        return isRpcRequest(request) ? ResponseEntity.badRequest().body(body) : ResponseEntity.ok(body);
    }

    private boolean isRpcRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return RPC_PATH_PREFIXES.stream().anyMatch(uri::startsWith);
    }

    /** 业务错误码映射 HTTP 状态：鉴权/权限/不存在/冲突/限流各归其位，其余 4xx；5xxxx 与服务错误码归 5xx */
    private int toHttpStatus(int code) {
        if (code == ErrorCode.UNAUTHORIZED.getCode() || code == ErrorCode.TOKEN_EXPIRED.getCode()) {
            return 401;
        }
        if (code == ErrorCode.FORBIDDEN.getCode()) {
            return 403;
        }
        if (code == ErrorCode.NOT_FOUND.getCode()) {
            return 404;
        }
        if (code == ErrorCode.DATA_CONFLICT.getCode()) {
            return 409;
        }
        if (code == ErrorCode.RATE_LIMITED.getCode()) {
            return 429;
        }
        if (code == ErrorCode.SYSTEM_ERROR.getCode() || code == ErrorCode.REMOTE_ERROR.getCode()
                || code == ErrorCode.ERROR.getCode()) {
            return 500;
        }
        return code >= 50000 ? 500 : 400;
    }

    /** HTTP 状态码对齐项目错误码，前端既能按状态码走错误分支，也能读 body.code 统一处理 */
    private ErrorCode toErrorCode(int status) {
        return switch (status) {
            case 400, 405, 406, 415 -> ErrorCode.PARAM_ERROR;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 409 -> ErrorCode.DATA_CONFLICT;
            case 429 -> ErrorCode.RATE_LIMITED;
            case 502, 503, 504 -> ErrorCode.REMOTE_ERROR;
            default -> ErrorCode.ERROR;
        };
    }

    private String firstMessage(List<? extends MessageSourceResolvable> errors) {
        return errors.stream()
                .findFirst()
                .map(MessageSourceResolvable::getDefaultMessage)
                .filter(m -> m != null && !m.isBlank())
                .orElse(ErrorCode.PARAM_ERROR.getMessage());
    }
}
