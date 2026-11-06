package com.weiyi.common.core.constant;

/**
 * 全局常量。
 */
public final class Constants {

    private Constants() {
    }

    /** 账号类型 */
    public static final String USER_TYPE_MEMBER = "M";
    public static final String USER_TYPE_ADMIN = "A";

    /** 网关向下游传递登录态的请求头 */
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_TYPE = "X-User-Type";
    public static final String HEADER_USERNAME = "X-Username";
    public static final String HEADER_USER_SUPER = "X-User-Super";
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    /** Authorization 前缀 */
    public static final String TOKEN_PREFIX = "Bearer ";

    /** 通用状态 */
    public static final int STATUS_ENABLED = 1;
    public static final int STATUS_DISABLED = 0;

    /** sys_user.user_type */
    public static final String ADMIN_TYPE_SUPER = "S";
    public static final String ADMIN_TYPE_NORMAL = "A";
}
