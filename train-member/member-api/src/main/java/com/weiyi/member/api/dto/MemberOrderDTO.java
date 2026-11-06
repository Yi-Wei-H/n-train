package com.weiyi.member.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单（服务间 RPC 契约，仅携带 AI 工具与展示所需字段）。
 */
@Data
public class MemberOrderDTO implements Serializable {

    private Long orderId;

    private String orderNo;

    /** 订单状态码（业务域定义） */
    private Integer orderStatus;

    private String orderStatusName;

    private LocalDate rideDate;

    private String trainCode;

    private String fromStation;

    private String fromName;

    private String toStation;

    private String toName;

    private LocalDateTime departureTime;

    /** 订单总额 */
    private BigDecimal totalAmount;

    /** 待支付截止时间（待支付订单给值） */
    private LocalDateTime payDeadline;

    private List<MemberOrderItemDTO> items;
}
