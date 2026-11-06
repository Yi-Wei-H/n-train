package com.weiyi.admin.shiro;

import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.authz.UnauthenticatedException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Shiro 鉴权异常出口：@RequiresPermissions 校验失败不再冒泡成 500。
 * admin 只有浏览器端点，沿用全站口径：HTTP 200，错误体现在 body.code。
 * 优先级高于 common-web 的兜底处理器，避免 AuthorizationException 被当成未知异常。
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class ShiroExceptionAdvice {

    @ExceptionHandler(UnauthenticatedException.class)
    public ResponseEntity<Result<Void>> handleUnauthenticated(UnauthenticatedException e, HttpServletRequest request) {
        log.warn("[未认证] {} {}", request.getMethod(), request.getRequestURI());
        return ResponseEntity.ok(Result.fail(ErrorCode.UNAUTHORIZED));
    }

    @ExceptionHandler(AuthorizationException.class)
    public ResponseEntity<Result<Void>> handleAuthorization(AuthorizationException e, HttpServletRequest request) {
        log.warn("[权限不足] {} {} message={}", request.getMethod(), request.getRequestURI(), e.getMessage());
        return ResponseEntity.ok(Result.fail(ErrorCode.FORBIDDEN, "该账号权限不足"));
    }
}
