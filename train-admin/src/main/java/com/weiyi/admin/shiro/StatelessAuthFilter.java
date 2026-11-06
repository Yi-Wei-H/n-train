package com.weiyi.admin.shiro;

import com.weiyi.common.core.constant.Constants;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.result.Result;
import com.weiyi.common.core.util.JacksonUtil;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.filter.AccessControlFilter;
import org.apache.shiro.web.util.WebUtils;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Shiro 无状态入口过滤器：从网关注入的头还原身份并 login()，
 * 使 @RequiresPermissions 注解可用；未带管理员身份 → 401 JSON。
 */
public class StatelessAuthFilter extends AccessControlFilter {

    @Override
    protected boolean isAccessAllowed(ServletRequest request, ServletResponse response, Object mappedValue) {
        Subject subject = SecurityUtils.getSubject();
        if (subject.isAuthenticated()) {
            return true;
        }
        String userId = WebUtils.toHttp(request).getHeader(Constants.HEADER_USER_ID);
        String userType = WebUtils.toHttp(request).getHeader(Constants.HEADER_USER_TYPE);
        if (userId != null && !userId.isBlank() && Constants.USER_TYPE_ADMIN.equals(userType)) {
            try {
                subject.login(new AdminToken(Long.parseLong(userId)));
                return true;
            } catch (AuthenticationException e) {
                return false;
            }
        }
        return false;
    }

    @Override
    protected boolean onAccessDenied(ServletRequest request, ServletResponse response) throws Exception {
        HttpServletResponse httpResponse = WebUtils.toHttp(response);
        httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        httpResponse.setContentType("application/json;charset=UTF-8");
        httpResponse.getWriter().write(JacksonUtil.toJson(Result.fail(ErrorCode.UNAUTHORIZED)));
        return false;
    }
}
