package com.weiyi.member.controller;

import com.weiyi.common.core.result.Result;
import com.weiyi.common.redis.dto.PublicKeyVO;
import com.weiyi.common.redis.ratelimit.LimitDimension;
import com.weiyi.common.redis.ratelimit.RateLimit;
import com.weiyi.common.redis.service.RsaKeyService;
import com.weiyi.common.security.context.LoginContext;
import com.weiyi.member.api.api.MemberUserApi;
import com.weiyi.member.api.dto.MemberDTO;
import com.weiyi.member.config.MemberProperties;
import com.weiyi.member.dto.LoginResult;
import com.weiyi.member.dto.LoginWebCmd;
import com.weiyi.member.dto.ProfileWebCmd;
import com.weiyi.member.dto.RegisterWebCmd;
import com.weiyi.member.dto.SendCodeWebCmd;
import com.weiyi.member.service.MemberAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会员账号：注册/登录（密码/短信双模式）/我的信息 + 图形验证码 + RSA 公钥下发。
 * 网关白名单：/user/login、/user/register、/user/public-key、/user/send-code、/captcha。
 * 手机号/身份证传输层 AES 加密（前端 aes.js encrypt，后端注解读密）。
 */
@RestController
@RequestMapping("/user")
public class MemberAuthController {

    private final MemberAuthService memberAuthService;
    private final MemberUserApi memberUserApi;
    private final RsaKeyService rsaKeyService;
    private final MemberProperties memberProperties;

    public MemberAuthController(MemberAuthService memberAuthService, MemberUserApi memberUserApi,
                                RsaKeyService rsaKeyService, MemberProperties memberProperties) {
        this.memberAuthService = memberAuthService;
        this.memberUserApi = memberUserApi;
        this.rsaKeyService = rsaKeyService;
        this.memberProperties = memberProperties;
    }

    /** 登录/注册前先取公钥加密密码，密文提交；每次取新钥（私钥 2 分钟内一次性） */
    @GetMapping("/public-key")
    @RateLimit(dimension = LimitDimension.IP, count = 30, interval = 60, message = "请求过于频繁，请稍后再试")
    public Result<PublicKeyVO> publicKey() {
        return Result.ok(rsaKeyService.createKey());
    }

    /** 发送短信验证码：需先通过图形验证码（防脚本刷短信）；模拟短信模式下回显验证码 */
    @PostMapping("/send-code")
    @RateLimit(dimension = LimitDimension.IP, count = 5, interval = 60, message = "发送过于频繁，请稍后再试")
    public Result<String> sendCode(@RequestBody @Valid SendCodeWebCmd cmd) {
        String code = memberAuthService.sendCode(cmd.getCaptchaKey(), cmd.getCaptchaCode(), cmd.getPhone());
        return Result.ok(memberProperties.isMockSmsEcho() ? code : null);
    }

    @PostMapping("/login")
    @RateLimit(dimension = LimitDimension.IP, count = 10, interval = 60,
            message = "登录过于频繁，请稍后再试")
    public Result<LoginResult> login(@RequestBody @Valid LoginWebCmd cmd,
                                     HttpServletRequest request) {
        return Result.ok(memberAuthService.login(cmd.getPhone(), cmd.getKeyId(), cmd.getCipher(),
                cmd.getSmsCode(), request));
    }

    @PostMapping("/register")
    @RateLimit(dimension = LimitDimension.IP, count = 5, interval = 60,
            message = "注册过于频繁，请稍后再试")
    public Result<MemberDTO> register(@RequestBody @Valid RegisterWebCmd cmd, HttpServletRequest request) {
        return Result.ok(memberAuthService.register(cmd.toServiceCmd(), request));
    }

    @GetMapping("/me")
    public Result<MemberDTO> me() {
        return Result.ok(memberAuthService.me());
    }

    /** 修改个人信息（真实姓名，服务端同步本人乘车人） */
    @PutMapping("/profile")
    public Result<Void> updateProfile(@RequestBody @Valid ProfileWebCmd cmd) {
        memberUserApi.updateProfile(LoginContext.requireMemberId(), cmd.getNickname());
        return Result.ok();
    }
}
