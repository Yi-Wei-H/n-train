package com.weiyi.admin.shiro;

import org.apache.shiro.authc.AuthenticationToken;

/**
 * 无状态管理员令牌：身份来自网关注入的请求头（网关已验签 JWT）。
 */
public class AdminToken implements AuthenticationToken {

    private final long adminId;

    public AdminToken(long adminId) {
        this.adminId = adminId;
    }

    @Override
    public Object getPrincipal() {
        return adminId;
    }

    @Override
    public Object getCredentials() {
        return "gateway-verified";
    }
}
