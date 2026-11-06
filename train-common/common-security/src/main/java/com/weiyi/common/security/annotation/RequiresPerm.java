package com.weiyi.common.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口权限标识（admin 服务用），如 @RequiresPerm("b:order:list")。
 * 校验逻辑见 PermissionInterceptor：超管直接放行，普通管理员匹配权限标识集合。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPerm {

    /** 权限标识，与 sys_menu.perms 对应 */
    String value();
}
