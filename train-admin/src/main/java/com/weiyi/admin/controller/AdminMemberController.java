package com.weiyi.admin.controller;

import com.weiyi.common.core.result.PageResult;
import com.weiyi.common.core.result.Result;
import com.weiyi.common.security.context.LoginContext;
import com.weiyi.admin.operlog.OperLog;
import com.weiyi.member.api.api.MemberUserApi;
import com.weiyi.member.api.dto.MemberDTO;
import com.weiyi.member.api.dto.MemberQuery;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会员管理（只读列表 + 启停）。
 */
@Slf4j
@RestController
@RequestMapping("/member")
public class AdminMemberController {

    private final MemberUserApi memberUserApi;

    public AdminMemberController(MemberUserApi memberUserApi) {
        this.memberUserApi = memberUserApi;
    }

    @GetMapping("/page")
    @RequiresPermissions("m:member:list")
    public Result<PageResult<MemberDTO>> page(@SpringQueryMap MemberQuery query) {
        return Result.ok(memberUserApi.page(query));
    }

    /** 重置会员密码为默认密码（MEMBER_DEFAULT_PASSWORD，见 secrets/.env；不允许自定义新密码） */
    @PutMapping("/{id}/password")
    @RequiresPermissions("m:member:resetPwd")
    @OperLog(module = "会员管理", operation = "重置会员密码")
    public Result<Void> resetPassword(@PathVariable long id) {
        log.info("管理端重置会员密码为默认值：operator={} memberId={}", LoginContext.requireAdminId(), id);
        memberUserApi.resetPassword(id);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    @RequiresPermissions("m:member:edit")
    @OperLog(module = "会员管理", operation = "变更会员状态")
    public Result<Void> updateStatus(@PathVariable long id, @RequestParam int status) {
        log.info("管理端变更会员状态：operator={} memberId={} status={}", LoginContext.requireAdminId(), id, status);
        memberUserApi.updateStatus(id, status);
        return Result.ok();
    }
}
