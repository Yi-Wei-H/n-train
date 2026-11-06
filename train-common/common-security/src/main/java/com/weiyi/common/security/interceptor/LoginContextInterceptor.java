package com.weiyi.common.security.interceptor;

import com.weiyi.common.core.constant.Constants;
import com.weiyi.common.security.context.LoginContext;
import com.weiyi.common.security.context.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 从网关注入的请求头还原登录态。只信任网关：入口在网关统一验签，
 * 服务侧收到的 X-User-* 头已在网关层清洗过伪造值。
 */
public class LoginContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = request.getHeader(Constants.HEADER_USER_ID);
        String userType = request.getHeader(Constants.HEADER_USER_TYPE);
        String username = request.getHeader(Constants.HEADER_USERNAME);
        String superAdmin = request.getHeader(Constants.HEADER_USER_SUPER);
        if (userId != null && !userId.isBlank() && userType != null && !userType.isBlank()) {
            LoginContext.set(new LoginUser(Long.valueOf(userId), userType, username,
                    "1".equals(superAdmin)));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        LoginContext.clear();
    }
}
