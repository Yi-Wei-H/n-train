package com.weiyi.member.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 退款申请结果（服务间 RPC 契约）。
 */
@Data
public class MemberRefundResultDTO implements Serializable {

    /** 退款批次号 */
    private String batchNo;

    /** 应退金额（本次申请合计） */
    private BigDecimal refundAmount;
}
