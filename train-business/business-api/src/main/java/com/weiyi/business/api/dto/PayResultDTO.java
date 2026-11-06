package com.weiyi.business.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/** 钱包支付结果 */
@Data
public class PayResultDTO implements Serializable {

    private Long orderId;
    private String orderNo;
    private String payNo;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
}
