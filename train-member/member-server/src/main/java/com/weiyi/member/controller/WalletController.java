package com.weiyi.member.controller;

import com.weiyi.common.core.result.PageResult;
import com.weiyi.common.core.result.Result;
import com.weiyi.common.redis.ratelimit.LimitDimension;
import com.weiyi.common.redis.ratelimit.RateLimit;
import com.weiyi.common.security.context.LoginContext;
import com.weiyi.member.dto.RechargeCmd;
import com.weiyi.member.dto.RechargeWebCmd;
import com.weiyi.member.dto.WalletDTO;
import com.weiyi.member.dto.WalletFlowDTO;
import com.weiyi.member.config.MemberProperties;
import com.weiyi.member.service.WalletTxService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 虚拟钱包：余额、充值（档位即充即到账）、流水。
 */
@RestController
@RequestMapping("/wallet")
public class WalletController {

    private final WalletTxService walletTxService;
    private final MemberProperties properties;

    public WalletController(WalletTxService walletTxService, MemberProperties properties) {
        this.walletTxService = walletTxService;
        this.properties = properties;
    }

    @GetMapping
    public Result<WalletDTO> get() {
        return Result.ok(walletTxService.toDTO(walletTxService.getWallet(LoginContext.requireMemberId())));
    }

    /** 充值档位（前端渲染充值页用） */
    @GetMapping("/tiers")
    public Result<List<BigDecimal>> tiers() {
        return Result.ok(properties.getRechargeTiers());
    }

    @PostMapping("/recharge")
    @RateLimit(dimension = LimitDimension.USER, count = 5, interval = 60, message = "充值操作过于频繁")
    public Result<WalletDTO> recharge(@RequestBody @jakarta.validation.Valid RechargeWebCmd webCmd) {
        RechargeCmd cmd = new RechargeCmd();
        cmd.setMemberId(LoginContext.requireMemberId());
        cmd.setAmount(webCmd.getAmount());
        return Result.ok(walletTxService.recharge(cmd));
    }

    @GetMapping("/flows")
    public Result<PageResult<WalletFlowDTO>> flows(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(walletTxService.flows(LoginContext.requireMemberId(), pageNum, pageSize));
    }
}
