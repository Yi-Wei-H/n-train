package com.weiyi.member.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 钱包流水 */
@Data
public class WalletFlowDTO implements Serializable {

    private String flowNo;
    /** 1 充值 2 支付 3 退款 */
    private Integer flowType;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String bizNo;
    private String remark;
    private LocalDateTime createdAt;
}
