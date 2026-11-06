package com.weiyi.member.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/** 充值命令（即充即到账，余额+本次 ≤1000 由数据端校验 + DB CHECK 双保险） */
@Data
public class RechargeCmd implements Serializable {

    @NotNull(message = "会员ID不能为空")
    private Long memberId;

    @NotNull(message = "充值金额不能为空")
    @DecimalMin(value = "0.01", message = "充值金额必须大于 0")
    private BigDecimal amount;
}
