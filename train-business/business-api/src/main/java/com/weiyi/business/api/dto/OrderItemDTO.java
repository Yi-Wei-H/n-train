package com.weiyi.business.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 订单明细 */
@Data
public class OrderItemDTO implements Serializable {

    private Long id;
    private Long passengerId;
    private String passengerName;
    private String idCardNo;
    /** 1 成人 2 儿童 3 学生 */
    private Integer ticketType;
    private Integer seatType;
    private Integer carriageNo;
    private String seatNo;
    private String fromStation;
    private String toStation;
    private BigDecimal price;
    private Integer itemStatus;
    private BigDecimal refundAmount;
    private LocalDateTime refundAt;
}
