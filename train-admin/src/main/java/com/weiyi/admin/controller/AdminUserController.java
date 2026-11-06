package com.weiyi.admin.controller;

import com.weiyi.common.core.constant.Constants;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.result.PageResult;
import com.weiyi.common.core.result.Result;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.common.security.context.LoginContext;
import com.weiyi.admin.service.SysUserService;
import com.weiyi.admin.dto.AdminSaveCmd;
import com.weiyi.admin.dto.AdminUserDTO;
import com.weiyi.admin.dto.AdminUserQuery;
import com.weiyi.admin.service.AdminPermService;
import com.weiyi.admin.operlog.OperLog;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员账号管理（变更后清权限缓存）。
 */
@RestController
@RequestMapping("/system/user")
public class AdminUserController {

    private final SysUserService sysUserService;
    private final AdminPermService adminPermService;

    public AdminUserController(SysUserService sysUserService, AdminPermService adminPermService) {
        this.sysUserService = sysUserService;
        this.adminPermService = adminPermService;
    }

    @GetMapping("/page")
    @RequiresPermissions("sys:user:list")
    public Result<PageResult<AdminUserDTO>> page(@SpringQueryMap AdminUserQuery query) {
        return Result.ok(sysUserService.page(query));
    }

    @GetMapping("/{id}")
    @RequiresPermissions("sys:user:list")
    public Result<AdminUserDTO> detail(@PathVariable long id) {
        return Result.ok(sysUserService.detail(id));
    }

    @PostMapping
    @RequiresPermissions("sys:user:add")
    @OperLog(module = "管理员管理", operation = "新增管理员")
    public Result<AdminUserDTO> add(@RequestBody AdminSaveCmd cmd) {
        AdminUserDTO added = sysUserService.add(cmd);
        adminPermService.invalidate(added.getId());
        return Result.ok(added);
    }

    @PutMapping
    @RequiresPermissions("sys:user:edit")
    @OperLog(module = "管理员管理", operation = "编辑管理员")
    public Result<AdminUserDTO> update(@RequestBody AdminSaveCmd cmd) {
        AdminUserDTO updated = sysUserService.update(cmd);
        adminPermService.invalidate(updated.getId());
        return Result.ok(updated);
    }

    @PutMapping("/{id}/status")
    @RequiresPermissions("sys:user:edit")
    @OperLog(module = "管理员管理", operation = "变更管理员状态")
    public Result<Void> updateStatus(@PathVariable long id, @RequestParam int status) {
        sysUserService.updateStatus(id, status);
        adminPermService.invalidate(id);
        return Result.ok();
    }

    @PutMapping("/{id}/roles")
    @RequiresPermissions("sys:user:edit")
    @OperLog(module = "管理员管理", operation = "分配管理员角色")
    public Result<Void> assignRoles(@PathVariable long id, @RequestBody List<Long> roleIds) {
        sysUserService.assignRoles(id, roleIds);
        adminPermService.invalidate(id);
        return Result.ok();
    }

    /** 重置为默认密码（不允许自定义新密码）；重置超管密码仅超管本人可操作，防普管借默认密码提权 */
    @PutMapping("/{id}/password")
    @RequiresPermissions("sys:user:resetPwd")
    @OperLog(module = "管理员管理", operation = "重置管理员密码")
    public Result<Void> resetPassword(@PathVariable long id) {
        AdminUserDTO target = sysUserService.detail(id);
        if (Constants.ADMIN_TYPE_SUPER.equals(target.getUserType())) {
            BizAssert.isTrue(adminPermService.isSuperAdmin(LoginContext.requireAdminId()),
                    ErrorCode.FORBIDDEN, "仅超级管理员可重置超管密码");
        }
        sysUserService.resetPassword(id);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermissions("sys:user:delete")
    @OperLog(module = "管理员管理", operation = "删除管理员")
    public Result<Void> delete(@PathVariable long id) {
        sysUserService.delete(id);
        adminPermService.invalidate(id);
        return Result.ok();
    }
}
