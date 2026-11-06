package com.weiyi.business.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/** 锁座命令：orderNo 即幂等键（一个订单一次锁座） */
@Data
public class SeatLockCmd implements Serializable {

    /** 订单号（占用记录关联键，幂等） */
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    /** 预生成的订单明细 ID（与座位一一对应，退款时按明细幂等释放） */
    @NotNull(message = "明细ID列表不能为空")
    private List<Long> orderItemIds;

    @NotNull(message = "乘车日期不能为空")
    private LocalDate rideDate;

    @NotBlank(message = "车次不能为空")
    private String trainCode;

    @NotBlank(message = "出发站不能为空")
    private String fromStation;

    @NotBlank(message = "到达站不能为空")
    private String toStation;

    @NotNull(message = "席别不能为空")
    private Integer seatType;

    @NotNull(message = "购票数不能为空")
    private Integer count;

    /** 用户自选座（抽象列位，如 "C1"/"D2"）；null/空 = 系统自动连座分配（SeatAllocator） */
    private List<String> chosenSeats;
}
