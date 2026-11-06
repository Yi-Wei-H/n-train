package com.weiyi.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiyi.common.core.constant.Constants;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.result.PageResult;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.common.core.util.PasswordUtil;
import com.weiyi.common.mybatis.util.PageUtils;
import com.weiyi.admin.dto.AdminAuthDTO;
import com.weiyi.admin.dto.AdminSaveCmd;
import com.weiyi.admin.dto.AdminUserDTO;
import com.weiyi.admin.dto.AdminUserQuery;
import com.weiyi.admin.dto.MenuNodeDTO;
import com.weiyi.admin.infra.entity.SysDept;
import com.weiyi.admin.infra.entity.SysMenu;
import com.weiyi.admin.infra.entity.SysRole;
import com.weiyi.admin.infra.entity.SysRoleMenu;
import com.weiyi.admin.infra.entity.SysUser;
import com.weiyi.admin.infra.entity.SysUserRole;
import com.weiyi.admin.infra.mapper.SysDeptMapper;
import com.weiyi.admin.infra.mapper.SysMenuMapper;
import com.weiyi.admin.infra.mapper.SysRoleMapper;
import com.weiyi.admin.infra.mapper.SysRoleMenuMapper;
import com.weiyi.admin.infra.mapper.SysUserMapper;
import com.weiyi.admin.infra.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理员账号 + RBAC 权限聚合（超管 user_type='S' 全量放行）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserService {

    private final SysUserMapper userMapper;

    @org.springframework.beans.factory.annotation.Value("${ntrain.user-defaults.super-admin-password}")
    private String superAdminDefaultPassword;

    @org.springframework.beans.factory.annotation.Value("${ntrain.user-defaults.admin-password}")
    private String adminDefaultPassword;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysMenuMapper menuMapper;
    private final SysDeptMapper deptMapper;

    // ==================== 登录态与权限 ====================

    public AdminAuthDTO getAuthByUsername(String username) {
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username));
        if (user == null) {
            return null;
        }
        AdminAuthDTO dto = new AdminAuthDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setPassword(user.getPassword());
        dto.setNickname(user.getNickname());
        dto.setUserType(user.getUserType());
        dto.setStatus(user.getStatus());
        dto.setDeptId(user.getDeptId());
        return dto;
    }

    /** 超管返回 {"*"}；普管返回角色聚合的权限标识集合 */
    public Set<String> loadPerms(long adminId) {
        SysUser user = userMapper.selectById(adminId);
        if (user == null) {
            return Set.of();
        }
        if (Constants.ADMIN_TYPE_SUPER.equals(user.getUserType())) {
            return Set.of("*");
        }
        Set<Long> roleIds = roleIdsOf(adminId);
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        List<Long> menuIds = roleMenuMapper.selectList(Wrappers.<SysRoleMenu>lambdaQuery()
                        .in(SysRoleMenu::getRoleId, roleIds)).stream()
                .map(SysRoleMenu::getMenuId).distinct().toList();
        if (menuIds.isEmpty()) {
            return Set.of();
        }
        return menuMapper.selectBatchIds(menuIds).stream()
                .map(SysMenu::getPerms)
                .filter(perms -> perms != null && !perms.isBlank())
                .collect(Collectors.toSet());
    }

    public Set<String> loadRoles(long adminId) {
        Set<Long> roleIds = roleIdsOf(adminId);
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        return roleMapper.selectBatchIds(roleIds).stream()
                .map(SysRole::getRoleKey).collect(Collectors.toSet());
    }

    /** 管理员可见菜单树（动态路由数据源）；超管返回全部启用菜单 */
    public List<MenuNodeDTO> loadMenus(long adminId) {
        SysUser user = userMapper.selectById(adminId);
        if (user == null) {
            return List.of();
        }
        List<SysMenu> menus;
        if (Constants.ADMIN_TYPE_SUPER.equals(user.getUserType())) {
            menus = menuMapper.selectList(Wrappers.<SysMenu>lambdaQuery()
                    .eq(SysMenu::getStatus, 1).orderByAsc(SysMenu::getOrderNum));
        } else {
            Set<Long> roleIds = roleIdsOf(adminId);
            if (roleIds.isEmpty()) {
                return List.of();
            }
            List<Long> menuIds = roleMenuMapper.selectList(Wrappers.<SysRoleMenu>lambdaQuery()
                            .in(SysRoleMenu::getRoleId, roleIds)).stream()
                    .map(SysRoleMenu::getMenuId).distinct().toList();
            if (menuIds.isEmpty()) {
                return List.of();
            }
            menus = menuMapper.selectBatchIds(menuIds).stream()
                    .filter(m -> m.getStatus() == 1)
                    .sorted(java.util.Comparator.comparing(SysMenu::getOrderNum))
                    .toList();
        }
        return MenuTreeBuilder.build(menus);
    }

    @Transactional
    public void updateLastLogin(long adminId) {
        userMapper.update(null, Wrappers.<SysUser>lambdaUpdate()
                .eq(SysUser::getId, adminId)
                .set(SysUser::getLastLoginAt, LocalDateTime.now()));
    }

    // ==================== 管理端 CRUD ====================

    public PageResult<AdminUserDTO> page(AdminUserQuery query) {
        Page<SysUser> page = userMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                Wrappers.<SysUser>lambdaQuery()
                        .like(query.getUsername() != null && !query.getUsername().isBlank(),
                                SysUser::getUsername, query.getUsername())
                        .eq(query.getStatus() != null, SysUser::getStatus, query.getStatus())
                        .eq(query.getDeptId() != null, SysUser::getDeptId, query.getDeptId())
                        .orderByDesc(SysUser::getCreatedAt));
        Map<Long, String> deptNames = deptMapper.selectList(Wrappers.emptyWrapper()).stream()
                .collect(Collectors.toMap(SysDept::getId, SysDept::getDeptName));
        Map<Long, List<Long>> roleIdsByUser = userRoleMapper.selectList(Wrappers.emptyWrapper()).stream()
                .collect(Collectors.groupingBy(SysUserRole::getUserId,
                        Collectors.mapping(SysUserRole::getRoleId, Collectors.toList())));
        return PageUtils.convert(page, u -> toDTO(u, deptNames.get(u.getDeptId()),
                roleIdsByUser.getOrDefault(u.getId(), List.of())));
    }

    public AdminUserDTO detail(long id) {
        SysUser user = userMapper.selectById(id);
        BizAssert.notNull(user, ErrorCode.ACCOUNT_NOT_EXISTS);
        String deptName = null;
        SysDept dept = deptMapper.selectById(user.getDeptId());
        if (dept != null) {
            deptName = dept.getDeptName();
        }
        return toDTO(user, deptName, new java.util.ArrayList<>(roleIdsOf(id)));
    }

    @Transactional
    public AdminUserDTO add(AdminSaveCmd cmd) {
        BizAssert.notBlank(cmd.getPassword(), ErrorCode.PARAM_ERROR, "初始密码不能为空");
        assertUsernameUnique(cmd.getUsername(), null);
        SysUser user = new SysUser();
        applyCmd(user, cmd);
        user.setPassword(PasswordUtil.hash(cmd.getPassword()));
        userMapper.insert(user);
        assignRoles(user.getId(), cmd.getRoleIds());
        log.info("管理员新增：id={} username={} 类型={} 部门={} 角色={}",
                user.getId(), user.getUsername(), user.getUserType(), user.getDeptId(), cmd.getRoleIds());
        return detail(user.getId());
    }

    @Transactional
    public AdminUserDTO update(AdminSaveCmd cmd) {
        SysUser user = userMapper.selectById(cmd.getId());
        BizAssert.notNull(user, ErrorCode.ACCOUNT_NOT_EXISTS);
        assertUsernameUnique(cmd.getUsername(), user.getId());
        applyCmd(user, cmd);
        // 管理端不允许编辑时自定义修改密码，只能走 resetPassword 重置为默认密码（防越权设弱口令）
        userMapper.updateById(user);
        if (cmd.getRoleIds() != null) {
            assignRoles(user.getId(), cmd.getRoleIds());
        }
        log.info("管理员修改：id={} username={} 类型={} 状态={} 角色={}",
                user.getId(), user.getUsername(), user.getUserType(), user.getStatus(), cmd.getRoleIds());
        return detail(user.getId());
    }

    @Transactional
    public void updateStatus(long id, int status) {
        SysUser user = userMapper.selectById(id);
        BizAssert.notNull(user, ErrorCode.ACCOUNT_NOT_EXISTS);
        log.info("管理员状态变更：id={} username={} {} → {}", id, user.getUsername(), user.getStatus(), status);
        user.setStatus(status);
        userMapper.updateById(user);
    }

    @Transactional
    public void assignRoles(long id, List<Long> roleIds) {
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, id));
        if (roleIds != null) {
            roleIds.stream().distinct().forEach(roleId -> {
                SysUserRole link = new SysUserRole();
                link.setUserId(id);
                link.setRoleId(roleId);
                userRoleMapper.insert(link);
            });
        }
        log.info("管理员角色分配：adminId={} 角色={}", id, roleIds);
    }

    /** 重置为默认密码：超管→SUPER_ADMIN_PASSWORD，普管→ADMIN_DEFAULT_PASSWORD（不允许自定义新密码） */
    @Transactional
    public void resetPassword(long id) {
        SysUser user = userMapper.selectById(id);
        BizAssert.notNull(user, ErrorCode.ACCOUNT_NOT_EXISTS);
        boolean superAdmin = Constants.ADMIN_TYPE_SUPER.equals(user.getUserType());
        String defaultPassword = superAdmin ? superAdminDefaultPassword : adminDefaultPassword;
        // 默认值来自环境变量 SUPER_ADMIN_PASSWORD / ADMIN_DEFAULT_PASSWORD
        BizAssert.notBlank(defaultPassword, ErrorCode.PARAM_ERROR, "系统未配置默认密码，请联系系统管理员");
        user.setPassword(PasswordUtil.hash(defaultPassword));
        userMapper.updateById(user);
        log.info("管理员密码重置为默认值：id={} username={} 超管={}", id, user.getUsername(), superAdmin);
    }

    @Transactional
    public void delete(long id) {
        SysUser user = userMapper.selectById(id);
        userMapper.deleteById(id);
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getUserId, id));
        log.info("管理员删除：id={} username={}", id, user == null ? null : user.getUsername());
    }

    // ==================== 内部 ====================

    private Set<Long> roleIdsOf(long adminId) {
        return userRoleMapper.selectList(Wrappers.<SysUserRole>lambdaQuery()
                        .eq(SysUserRole::getUserId, adminId)).stream()
                .map(SysUserRole::getRoleId).collect(Collectors.toSet());
    }

    private void assertUsernameUnique(String username, Long excludeId) {
        Long count = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username)
                .ne(excludeId != null, SysUser::getId, excludeId));
        BizAssert.isTrue(count == 0, ErrorCode.PARAM_ERROR, "登录名已存在：" + username);
    }

    private void applyCmd(SysUser user, AdminSaveCmd cmd) {
        user.setUsername(cmd.getUsername());
        user.setNickname(cmd.getNickname());
        user.setPhone(cmd.getPhone());
        user.setEmail(cmd.getEmail());
        user.setDeptId(cmd.getDeptId() == null ? 0L : cmd.getDeptId());
        user.setUserType(cmd.getUserType() == null ? Constants.ADMIN_TYPE_NORMAL : cmd.getUserType());
        user.setStatus(cmd.getStatus() == null ? 1 : cmd.getStatus());
    }

    private AdminUserDTO toDTO(SysUser user, String deptName, List<Long> roleIds) {
        AdminUserDTO dto = new AdminUserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setPhone(user.getPhone());
        dto.setEmail(user.getEmail());
        dto.setDeptId(user.getDeptId());
        dto.setDeptName(deptName);
        dto.setUserType(user.getUserType());
        dto.setStatus(user.getStatus());
        dto.setLastLoginAt(user.getLastLoginAt());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setRoleIds(roleIds);
        return dto;
    }

    /**
     * 菜单树构建（SysMenuService 复用）。
     */
    static List<MenuNodeDTO> buildMenuTree(List<SysMenu> menus) {
        return MenuTreeBuilder.build(menus);
    }
}
