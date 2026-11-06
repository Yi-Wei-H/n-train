package com.weiyi.admin.controller;

import com.weiyi.common.core.result.Result;
import com.weiyi.common.redis.dto.PublicKeyVO;
import com.weiyi.common.redis.ratelimit.LimitDimension;
import com.weiyi.common.redis.ratelimit.RateLimit;
import com.weiyi.common.redis.service.RsaKeyService;
import com.weiyi.common.security.context.LoginContext;
import com.weiyi.admin.dto.AdminUserDTO;
import com.weiyi.admin.dto.LoginCmd;
import com.weiyi.admin.dto.LoginResult;
import com.weiyi.admin.dto.MenuNodeDTO;
import com.weiyi.admin.service.AdminAuthService;
import com.weiyi.admin.service.AdminPermService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端认证：登录（网关白名单）/ 我的信息 / 动态菜单树。
 */
@RestController
@RequestMapping("/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final AdminPermService adminPermService;
    private final RsaKeyService rsaKeyService;

    public AdminAuthController(AdminAuthService adminAuthService, AdminPermService adminPermService,
                               RsaKeyService rsaKeyService) {
        this.adminAuthService = adminAuthService;
        this.adminPermService = adminPermService;
        this.rsaKeyService = rsaKeyService;
    }

    /** 登录前先取公钥加密密码，密文提交；每次取新钥（私钥 2 分钟内一次性） */
    @GetMapping("/public-key")
    @RateLimit(dimension = LimitDimension.IP, count = 30, interval = 60, message = "请求过于频繁，请稍后再试")
    public Result<PublicKeyVO> publicKey() {
        return Result.ok(rsaKeyService.createKey());
    }

    @PostMapping("/login")
    @RateLimit(dimension = LimitDimension.IP, count = 10, interval = 60, message = "登录尝试过于频繁")
    public Result<LoginResult> login(@RequestBody LoginCmd cmd, HttpServletRequest request) {
        return Result.ok(adminAuthService.login(cmd, request));
    }

    @GetMapping("/me")
    public Result<AdminUserDTO> me() {
        return Result.ok(adminAuthService.me());
    }

    /** 动态菜单树（前端据此生成路由；普管/超管所见不同） */
    @GetMapping("/menus")
    public Result<List<MenuNodeDTO>> menus() {
        return Result.ok(adminPermService.loadMenus(LoginContext.requireAdminId()));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        LoginContext.requireAdminId();
        return Result.ok();
    }
}
