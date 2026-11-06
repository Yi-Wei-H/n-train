package com.weiyi.business.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/** 退票结果 */
@Data
public class RefundResultDTO implements Serializable {

    private Long orderId;
    private BigDecimal refundedAmount;
    private BigDecimal balanceAfter;
    private List<Long> refundedItemIds;
    /** 退后订单是否已整体关闭 */
    private boolean orderClosed;
}
