package com.weiyi.admin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.weiyi.common.core.constant.Constants;
import com.weiyi.common.redis.constant.RedisKeys;
import com.weiyi.common.redis.service.RedisService;
import com.weiyi.common.security.auth.PermissionResolver;
import com.weiyi.admin.service.SysUserService;
import com.weiyi.admin.dto.AdminUserDTO;
import com.weiyi.admin.dto.MenuNodeDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * 管理员权限聚合（common-security PermissionResolver 的实现）：
 * SysUserService 直读 + Redis 缓存 10 分钟；角色/菜单/管理员变更时失效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminPermService implements PermissionResolver {

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    private final SysUserService sysUserService;
    private final RedisService redisService;

    @Override
    public boolean isSuperAdmin(long adminId) {
        String key = RedisKeys.adminSuper(adminId);
        String flag = redisService.get(key, String.class);
        if (flag == null) {
            AdminUserDTO admin = sysUserService.detail(adminId);
            flag = Constants.ADMIN_TYPE_SUPER.equals(admin.getUserType()) ? "1" : "0";
            redisService.set(key, flag, CACHE_TTL);
            log.debug("超管标记缓存回源：adminId={} 超管={}", adminId, "1".equals(flag));
        }
        return "1".equals(flag);
    }

    @Override
    public Set<String> loadPerms(long adminId) {
        return redisService.getOrLoad(RedisKeys.adminPerms(adminId),
                new TypeReference<Set<String>>() {
                },
                CACHE_TTL,
                () -> sysUserService.loadPerms(adminId));
    }

    public List<MenuNodeDTO> loadMenus(long adminId) {
        return redisService.getOrLoad(RedisKeys.adminMenus(adminId),
                new TypeReference<List<MenuNodeDTO>>() {
                },
                CACHE_TTL,
                () -> sysUserService.loadMenus(adminId));
    }

    public Set<String> loadRoles(long adminId) {
        return redisService.getOrLoad(RedisKeys.adminRoles(adminId),
                new TypeReference<Set<String>>() {
                },
                CACHE_TTL,
                () -> sysUserService.loadRoles(adminId));
    }

    /** 指定管理员的权限缓存失效 */
    public void invalidate(long adminId) {
        log.info("权限缓存失效：adminId={}（权限/菜单/角色/超管标记）", adminId);
        redisService.delete(RedisKeys.adminPerms(adminId));
        redisService.delete(RedisKeys.adminMenus(adminId));
        redisService.delete(RedisKeys.adminRoles(adminId));
        redisService.delete(RedisKeys.adminSuper(adminId));
    }

    /** 角色或菜单变更 → 清全部管理员权限缓存（数据量小，粗暴但正确） */
    public void invalidateAll() {
        log.info("权限缓存全量失效（角色或菜单变更）");
        redisService.deleteByPattern(RedisKeys.ADMIN_CACHE_PATTERN);
    }
}
