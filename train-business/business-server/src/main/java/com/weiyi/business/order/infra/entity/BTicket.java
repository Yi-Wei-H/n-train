package com.weiyi.business.order.infra.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.weiyi.common.mybatis.handler.AesCryptoTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "b_ticket", autoResultMap = true)
public class BTicket extends BaseTimeEntity {

    private Long memberId;
    private Long orderId;
    private Long orderItemId;
    private Long passengerId;
    private String passengerName;
    /** 证件号（库内 AES 加密存储，读取自动解密） */
    @TableField(typeHandler = AesCryptoTypeHandler.class)
    private String idCardNo;
    private LocalDate rideDate;
    private String trainCode;
    private String fromStation;
    private String toStation;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private Integer seatType;
    private Integer carriageNo;
    private String seatNo;
    /** 1 待出行 2 已出行 3 已退票 4 已失效 */
    private Integer ticketStatus;
    private BigDecimal price;
}
