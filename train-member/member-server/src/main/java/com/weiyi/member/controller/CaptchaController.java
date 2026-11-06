package com.weiyi.member.controller;

import com.weiyi.common.core.result.Result;
import com.weiyi.member.dto.CaptchaImage;
import com.weiyi.member.service.CaptchaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 图形验证码（网关白名单接口）。
 */
@RestController
@RequestMapping("/captcha")
public class CaptchaController {

    private final CaptchaService captchaService;

    public CaptchaController(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    @GetMapping
    public Result<CaptchaImage> generate() {
        return Result.ok(captchaService.generate());
    }
}
