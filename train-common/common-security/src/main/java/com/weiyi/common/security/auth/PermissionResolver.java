package com.weiyi.common.security.auth;

import java.util.Set;

/**
 * 管理员权限集 SPI：由 admin 服务实现（SysUserService 直读 + Redis 缓存），
 * common-security 只依赖接口，保持模块零耦合。
 */
public interface PermissionResolver {

    /** 是否超级管理员（sys_user.user_type='S'，代码直接放行） */
    boolean isSuperAdmin(long adminId);

    /** 管理员权限标识集合（角色 → 菜单 perms 聚合） */
    Set<String> loadPerms(long adminId);
}
