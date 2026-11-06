package com.weiyi.business.order.infra.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.weiyi.common.mybatis.handler.AesCryptoTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "b_order_item", autoResultMap = true)
public class BOrderItem extends BaseTimeEntity {

    private Long orderId;
    private Long memberId;
    private Long passengerId;
    private String passengerName;
    @TableField(typeHandler = AesCryptoTypeHandler.class)
    private String idCardNo;
    /** 1 成人 2 儿童 3 学生 */
    private Integer ticketType;
    private Integer seatType;
    private Integer carriageNo;
    private String seatNo;
    private String fromStation;
    private String toStation;
    private BigDecimal price;
    /** 0 待支付 1 已支付 2 已出行 3 退票中 4 已退票 */
    private Integer itemStatus;
    private BigDecimal refundAmount;
    private LocalDateTime refundAt;
    /** 退款批次号（apply 时写入，confirm 按批次定位明细） */
    private String refundBatchNo;
}
