package com.weiyi.member.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/** 钱包充值命令（档位即充即到账，金额合法性由服务端 RechargeCmd 校验） */
@Data
public class RechargeWebCmd implements Serializable {

    private BigDecimal amount;
}
