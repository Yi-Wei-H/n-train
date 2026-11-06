package com.weiyi.admin.controller;

import com.weiyi.common.core.result.PageResult;
import com.weiyi.common.core.result.Result;
import com.weiyi.admin.service.LogService;
import com.weiyi.admin.dto.LoginLogDTO;
import com.weiyi.admin.dto.LogQuery;
import com.weiyi.admin.dto.OperLogDTO;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 日志查询（登录/操作）。
 */
@RestController
@RequestMapping("/system/log")
public class AdminLogController {

    private final LogService logService;

    public AdminLogController(LogService logService) {
        this.logService = logService;
    }

    @GetMapping("/login/page")
    @RequiresPermissions("sys:log:list")
    public Result<PageResult<LoginLogDTO>> loginLogs(@SpringQueryMap LogQuery query) {
        return Result.ok(logService.pageLoginLogs(query));
    }

    @GetMapping("/oper/page")
    @RequiresPermissions("sys:log:list")
    public Result<PageResult<OperLogDTO>> operLogs(@SpringQueryMap LogQuery query) {
        return Result.ok(logService.pageOperLogs(query));
    }
}
