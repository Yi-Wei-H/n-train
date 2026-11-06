package com.weiyi.admin.controller;

import com.weiyi.common.core.result.Result;
import com.weiyi.admin.service.SysRoleService;
import com.weiyi.admin.dto.RoleDTO;
import com.weiyi.admin.service.AdminPermService;
import com.weiyi.admin.operlog.OperLog;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理（变更后清全部权限缓存）。
 */
@RestController
@RequestMapping("/system/role")
public class AdminRoleController {

    private final SysRoleService sysRoleService;
    private final AdminPermService adminPermService;

    public AdminRoleController(SysRoleService sysRoleService, AdminPermService adminPermService) {
        this.sysRoleService = sysRoleService;
        this.adminPermService = adminPermService;
    }

    @GetMapping("/list")
    @RequiresPermissions("sys:role:list")
    public Result<List<RoleDTO>> list() {
        return Result.ok(sysRoleService.listAll());
    }

    @PostMapping
    @RequiresPermissions("sys:role:add")
    @OperLog(module = "角色管理", operation = "新增角色")
    public Result<RoleDTO> save(@RequestBody RoleDTO role) {
        RoleDTO saved = sysRoleService.save(role);
        adminPermService.invalidateAll();
        return Result.ok(saved);
    }

    @PutMapping
    @RequiresPermissions("sys:role:edit")
    @OperLog(module = "角色管理", operation = "编辑角色")
    public Result<RoleDTO> update(@RequestBody RoleDTO role) {
        RoleDTO updated = sysRoleService.update(role);
        adminPermService.invalidateAll();
        return Result.ok(updated);
    }

    @DeleteMapping("/{id}")
    @RequiresPermissions("sys:role:delete")
    @OperLog(module = "角色管理", operation = "删除角色")
    public Result<Void> delete(@PathVariable long id) {
        sysRoleService.delete(id);
        adminPermService.invalidateAll();
        return Result.ok();
    }

    @GetMapping("/{id}/menu-ids")
    @RequiresPermissions("sys:role:list")
    public Result<List<Long>> menuIds(@PathVariable long id) {
        return Result.ok(sysRoleService.menuIds(id));
    }

    @PutMapping("/{id}/menus")
    @RequiresPermissions("sys:role:edit")
    @OperLog(module = "角色管理", operation = "分配角色菜单")
    public Result<Void> assignMenus(@PathVariable long id, @RequestBody List<Long> menuIds) {
        sysRoleService.assignMenus(id, menuIds);
        adminPermService.invalidateAll();
        return Result.ok();
    }
}
