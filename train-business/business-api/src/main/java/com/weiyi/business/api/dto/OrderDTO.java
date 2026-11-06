package com.weiyi.business.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 订单（主表 + 明细） */
@Data
public class OrderDTO implements Serializable {

    private Long id;
    private String orderNo;
    private Long memberId;
    private LocalDate rideDate;
    private String trainCode;
    private String fromStation;
    private String toStation;
    private String fromName;
    private String toName;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private BigDecimal totalAmount;
    private Integer orderStatus;
    private LocalDateTime payAt;
    private LocalDateTime cancelAt;
    private Integer cancelType;
    private LocalDateTime closedAt;
    private LocalDateTime createdAt;
    private List<OrderItemDTO> items;

    /** 仅下单响应携带：true=所选自选座不可用已自动分配其他座位（详情/列表为 null） */
    private Boolean seatDegraded;
}
