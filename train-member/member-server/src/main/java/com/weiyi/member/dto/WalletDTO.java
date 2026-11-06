package com.weiyi.member.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/** 虚拟钱包 */
@Data
public class WalletDTO implements Serializable {

    private Long memberId;
    private BigDecimal balance;
    /** 1 正常 0 冻结 */
    private Integer status;
    /** 可充值剩余额度（上限 1000） */
    private BigDecimal rechargeQuota;
}
