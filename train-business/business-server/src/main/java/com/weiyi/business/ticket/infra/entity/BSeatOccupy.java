package com.weiyi.business.ticket.infra.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 座位占用记录（锁座的落库凭据）：下单锁定、关单/退款释放，
 * status 条件更新（1占用→2已释放）保证 MQ 重复消费幂等、余票回补不重复。
 */
@Data
@TableName("b_seat_occupy")
public class BSeatOccupy implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 订单号 = 锁座幂等键 */
    private String orderNo;

    /** 订单明细 ID（退款按明细释放） */
    private Long orderItemId;

    private LocalDate rideDate;

    private String trainCode;

    private String fromStation;

    private String toStation;

    private Integer seatType;

    private Integer carriageNo;

    private Integer rowNo;

    private String colNo;

    /** 1占用 2已释放 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
