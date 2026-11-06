package com.weiyi.business.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/** 支付前置校验结果（member 扣钱包的依据） */
@Data
public class PayableDTO implements Serializable {

    private Long orderId;

    private String orderNo;

    private BigDecimal amount;
}
