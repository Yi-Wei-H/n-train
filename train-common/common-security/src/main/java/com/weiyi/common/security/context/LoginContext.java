package com.weiyi.common.security.context;

import com.weiyi.common.core.constant.Constants;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;

/**
 * 登录上下文（请求级 ThreadLocal）。
 * 写入点：LoginContextInterceptor；清理点：afterCompletion，杜绝线程池串号。
 */
public final class LoginContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private LoginContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static Long getUserId() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUserId();
    }

    public static String getUserType() {
        LoginUser user = HOLDER.get();
        return user == null ? null : user.getUserType();
    }

    /** 要求已登录会员（member 服务用） */
    public static Long requireMemberId() {
        LoginUser user = require();
        if (!Constants.USER_TYPE_MEMBER.equals(user.getUserType())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return user.getUserId();
    }

    /** 要求已登录管理员（admin 服务用） */
    public static Long requireAdminId() {
        LoginUser user = require();
        if (!Constants.USER_TYPE_ADMIN.equals(user.getUserType())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return user.getUserId();
    }

    /** 要求已登录超管（task 服务的 Job 管理台用，标识由管理端登录时签进 token） */
    public static Long requireSuperAdmin() {
        LoginUser user = require();
        if (!Constants.USER_TYPE_ADMIN.equals(user.getUserType())
                || !Boolean.TRUE.equals(user.getSuperAdmin())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅超级管理员可操作");
        }
        return user.getUserId();
    }

    public static void clear() {
        HOLDER.remove();
    }

    private static LoginUser require() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }
}
