package com.weiyi.business.order.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("b_order")
public class BOrder extends BaseTimeEntity {

    private String orderNo;
    private Long memberId;
    private LocalDate rideDate;
    private String trainCode;
    private String fromStation;
    private String toStation;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private BigDecimal totalAmount;
    /** 0 待支付 1 已支付 2 已出行 3 已完成 4 已取消 5 退款中 6 已关闭 */
    private Integer orderStatus;
    private LocalDateTime payAt;
    private LocalDateTime cancelAt;
    /** 1 超时自动 2 用户手动 */
    private Integer cancelType;
    private LocalDateTime closedAt;
}
