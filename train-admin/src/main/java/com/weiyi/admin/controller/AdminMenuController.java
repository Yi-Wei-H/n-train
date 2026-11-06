package com.weiyi.admin.controller;

import com.weiyi.common.core.result.Result;
import com.weiyi.admin.service.SysMenuService;
import com.weiyi.admin.dto.MenuNodeDTO;
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
 * 菜单管理（变更后清全部权限/菜单缓存）。
 */
@RestController
@RequestMapping("/system/menu")
public class AdminMenuController {

    private final SysMenuService sysMenuService;
    private final AdminPermService adminPermService;

    public AdminMenuController(SysMenuService sysMenuService, AdminPermService adminPermService) {
        this.sysMenuService = sysMenuService;
        this.adminPermService = adminPermService;
    }

    @GetMapping("/tree")
    @RequiresPermissions("sys:menu:list")
    public Result<List<MenuNodeDTO>> tree() {
        return Result.ok(sysMenuService.tree());
    }

    @PostMapping
    @RequiresPermissions("sys:menu:add")
    @OperLog(module = "菜单管理", operation = "新增菜单")
    public Result<MenuNodeDTO> save(@RequestBody MenuNodeDTO menu) {
        MenuNodeDTO saved = sysMenuService.save(menu);
        adminPermService.invalidateAll();
        return Result.ok(saved);
    }

    @PutMapping
    @RequiresPermissions("sys:menu:edit")
    @OperLog(module = "菜单管理", operation = "编辑菜单")
    public Result<MenuNodeDTO> update(@RequestBody MenuNodeDTO menu) {
        MenuNodeDTO updated = sysMenuService.update(menu);
        adminPermService.invalidateAll();
        return Result.ok(updated);
    }

    @DeleteMapping("/{id}")
    @RequiresPermissions("sys:menu:delete")
    @OperLog(module = "菜单管理", operation = "删除菜单")
    public Result<Void> delete(@PathVariable long id) {
        sysMenuService.delete(id);
        adminPermService.invalidateAll();
        return Result.ok();
    }
}
