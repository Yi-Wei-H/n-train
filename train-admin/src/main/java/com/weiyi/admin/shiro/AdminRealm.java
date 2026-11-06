package com.weiyi.admin.shiro;

import com.weiyi.common.core.constant.Constants;
import com.weiyi.admin.service.SysUserService;
import com.weiyi.admin.dto.AdminUserDTO;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.LockedAccountException;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;

/**
 * 无状态 Realm：认证仅确认账号存在且启用（登录态由网关保证），
 * 授权走 PermissionResolver（SysUserService 权限标识 + Redis 缓存）。
 */
public class AdminRealm extends AuthorizingRealm {

    private final SysUserService sysUserService;
    private final com.weiyi.common.security.auth.PermissionResolver permissionResolver;

    public AdminRealm(SysUserService sysUserService,
                      com.weiyi.common.security.auth.PermissionResolver permissionResolver) {
        this.sysUserService = sysUserService;
        this.permissionResolver = permissionResolver;
        setCachingEnabled(false); // 缓存在 PermissionResolver 侧统一做
    }

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof AdminToken;
    }

    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken token) {
        long adminId = (Long) token.getPrincipal();
        AdminUserDTO admin = sysUserService.detail(adminId);
        if (admin.getStatus() == null || admin.getStatus() != 1) {
            throw new LockedAccountException("账号已被禁用");
        }
        return new SimpleAuthenticationInfo(adminId, token.getCredentials(), getName());
    }

    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        long adminId = (Long) principals.getPrimaryPrincipal();
        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
        info.setStringPermissions(permissionResolver.loadPerms(adminId));
        return info;
    }
}
