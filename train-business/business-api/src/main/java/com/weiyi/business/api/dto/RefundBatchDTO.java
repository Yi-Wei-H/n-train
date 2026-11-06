package com.weiyi.business.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/** 退款批次（member 入账依据；batchNo 同时是钱包流水幂等键与确认幂等键） */
@Data
public class RefundBatchDTO implements Serializable {

    private String batchNo;

    private Long orderId;

    private String orderNo;

    private List<Long> itemIds;

    private BigDecimal refundAmount;
}
