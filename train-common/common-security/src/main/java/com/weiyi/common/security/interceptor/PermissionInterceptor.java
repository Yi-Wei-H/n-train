package com.weiyi.common.security.interceptor;

import com.weiyi.common.core.constant.Constants;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.security.annotation.RequiresPerm;
import com.weiyi.common.security.auth.PermissionResolver;
import com.weiyi.common.security.context.LoginContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * @RequiresPerm 校验：超管放行，普管校验权限标识。
 * PermissionResolver 由 admin 服务提供；未提供时（如 member 服务）不启用校验。
 */
public class PermissionInterceptor implements HandlerInterceptor {

    private final PermissionResolver resolver;

    public PermissionInterceptor(PermissionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RequiresPerm requiresPerm = handlerMethod.getMethodAnnotation(RequiresPerm.class);
        if (requiresPerm == null) {
            requiresPerm = handlerMethod.getBeanType().getAnnotation(RequiresPerm.class);
        }
        if (requiresPerm == null || resolver == null) {
            return true;
        }
        Long adminId = LoginContext.getUserId();
        if (adminId == null || !Constants.USER_TYPE_ADMIN.equals(LoginContext.getUserType())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (resolver.isSuperAdmin(adminId)) {
            return true;
        }
        Set<String> perms = resolver.loadPerms(adminId);
        if (perms == null || !perms.contains(requiresPerm.value())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return true;
    }
}
