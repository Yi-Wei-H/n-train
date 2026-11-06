package com.weiyi.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.admin.dto.RoleDTO;
import com.weiyi.admin.infra.entity.SysRole;
import com.weiyi.admin.infra.entity.SysRoleMenu;
import com.weiyi.admin.infra.entity.SysUserRole;
import com.weiyi.admin.infra.mapper.SysRoleMapper;
import com.weiyi.admin.infra.mapper.SysRoleMenuMapper;
import com.weiyi.admin.infra.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 角色管理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysRoleService {

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysUserRoleMapper userRoleMapper;

    public List<RoleDTO> listAll() {
        List<RoleDTO> roles = roleMapper.selectList(Wrappers.<SysRole>lambdaQuery()
                .orderByDesc(SysRole::getCreatedAt)).stream().map(this::toDTO).toList();
        log.debug("角色列表查询：命中 {} 个角色", roles.size());
        return roles;
    }

    @Transactional
    public RoleDTO save(RoleDTO cmd) {
        assertKeyUnique(cmd.getRoleKey(), null);
        SysRole role = new SysRole();
        apply(cmd, role);
        roleMapper.insert(role);
        log.info("角色新增：id={} roleKey={} roleName={}", role.getId(), role.getRoleKey(), role.getRoleName());
        return toDTO(role);
    }

    @Transactional
    public RoleDTO update(RoleDTO cmd) {
        SysRole role = roleMapper.selectById(cmd.getId());
        BizAssert.notNull(role, ErrorCode.NOT_FOUND);
        BizAssert.isTrue(role.getBuiltIn() == null || role.getBuiltIn() != 1 || role.getRoleKey().equals(cmd.getRoleKey()),
                ErrorCode.PARAM_ERROR, "内置角色不允许改角色标识");
        assertKeyUnique(cmd.getRoleKey(), role.getId());
        log.info("角色修改：id={} roleKey {} → {} roleName {} → {}",
                role.getId(), role.getRoleKey(), cmd.getRoleKey(), role.getRoleName(), cmd.getRoleName());
        apply(cmd, role);
        roleMapper.updateById(role);
        return toDTO(role);
    }

    @Transactional
    public void delete(long id) {
        SysRole role = roleMapper.selectById(id);
        BizAssert.notNull(role, ErrorCode.NOT_FOUND);
        BizAssert.isTrue(role.getBuiltIn() == null || role.getBuiltIn() != 1,
                ErrorCode.PARAM_ERROR, "内置角色不允许删除");
        roleMapper.deleteById(id);
        roleMenuMapper.delete(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, id));
        userRoleMapper.delete(Wrappers.<SysUserRole>lambdaQuery().eq(SysUserRole::getRoleId, id));
        log.info("角色删除：id={} roleKey={}（同时解除菜单与管理员关联）", id, role.getRoleKey());
    }

    public List<Long> menuIds(long roleId) {
        return roleMenuMapper.selectList(Wrappers.<SysRoleMenu>lambdaQuery()
                        .eq(SysRoleMenu::getRoleId, roleId)).stream()
                .map(SysRoleMenu::getMenuId).toList();
    }

    @Transactional
    public void assignMenus(long roleId, List<Long> menuIds) {
        roleMenuMapper.delete(Wrappers.<SysRoleMenu>lambdaQuery().eq(SysRoleMenu::getRoleId, roleId));
        if (menuIds != null) {
            menuIds.stream().distinct().forEach(menuId -> {
                SysRoleMenu link = new SysRoleMenu();
                link.setRoleId(roleId);
                link.setMenuId(menuId);
                roleMenuMapper.insert(link);
            });
        }
        log.info("角色菜单授权：roleId={} 菜单 {} 个：{}", roleId,
                menuIds == null ? 0 : menuIds.size(), menuIds);
    }

    private void assertKeyUnique(String roleKey, Long excludeId) {
        Long count = roleMapper.selectCount(Wrappers.<SysRole>lambdaQuery()
                .eq(SysRole::getRoleKey, roleKey)
                .ne(excludeId != null, SysRole::getId, excludeId));
        BizAssert.isTrue(count == 0, ErrorCode.PARAM_ERROR, "角色标识已存在：" + roleKey);
    }

    private void apply(RoleDTO cmd, SysRole role) {
        role.setRoleName(cmd.getRoleName());
        role.setRoleKey(cmd.getRoleKey());
        role.setStatus(cmd.getStatus() == null ? 1 : cmd.getStatus());
        role.setRemark(cmd.getRemark());
    }

    private RoleDTO toDTO(SysRole role) {
        RoleDTO dto = new RoleDTO();
        dto.setId(role.getId());
        dto.setRoleName(role.getRoleName());
        dto.setRoleKey(role.getRoleKey());
        dto.setBuiltIn(role.getBuiltIn());
        dto.setStatus(role.getStatus());
        dto.setRemark(role.getRemark());
        dto.setCreatedAt(role.getCreatedAt());
        return dto;
    }
}
