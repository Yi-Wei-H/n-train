package com.weiyi.gateway.dto;

/** 网关 JWT 校验通过后的登录态（AuthGlobalFilter 据此注入 X-User-* 头透传下游） */
public record TokenUser(long userId, String userType, String username, boolean superAdmin) {
}
