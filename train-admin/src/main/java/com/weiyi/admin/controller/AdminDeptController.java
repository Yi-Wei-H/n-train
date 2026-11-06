package com.weiyi.admin.controller;

import com.weiyi.common.core.result.Result;
import com.weiyi.admin.service.SysDeptService;
import com.weiyi.admin.dto.DeptNodeDTO;
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
 * 部门管理（ancestors 部门链）。
 */
@RestController
@RequestMapping("/system/dept")
public class AdminDeptController {

    private final SysDeptService sysDeptService;

    public AdminDeptController(SysDeptService sysDeptService) {
        this.sysDeptService = sysDeptService;
    }

    @GetMapping("/tree")
    @RequiresPermissions("sys:dept:list")
    public Result<List<DeptNodeDTO>> tree() {
        return Result.ok(sysDeptService.tree());
    }

    @PostMapping
    @RequiresPermissions("sys:dept:add")
    @OperLog(module = "部门管理", operation = "新增部门")
    public Result<DeptNodeDTO> save(@RequestBody DeptNodeDTO dept) {
        return Result.ok(sysDeptService.save(dept));
    }

    @PutMapping
    @RequiresPermissions("sys:dept:edit")
    @OperLog(module = "部门管理", operation = "编辑部门")
    public Result<DeptNodeDTO> update(@RequestBody DeptNodeDTO dept) {
        return Result.ok(sysDeptService.update(dept));
    }

    @DeleteMapping("/{id}")
    @RequiresPermissions("sys:dept:delete")
    @OperLog(module = "部门管理", operation = "删除部门")
    public Result<Void> delete(@PathVariable long id) {
        sysDeptService.delete(id);
        return Result.ok();
    }
}
