package com.weiyi.business.ticket.dto;

import com.weiyi.business.api.dto.SeatDTO;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 锁座结果：座位明细 + 单价 + 到发时刻（订单域据此落订单与明细） */
@Data
public class SeatLockResult implements Serializable {

    /** 与入参 orderNo 一致（幂等重放时原样返回） */
    private String orderNo;

    /** 每个乘车人一个座位，顺序即分配顺序 */
    private List<SeatDTO> seats;

    /** 该席别站对单价 */
    private BigDecimal unitPrice;

    /** 出发/到达时刻（订单快照用） */
    private LocalDateTime departureTime;

    private LocalDateTime arrivalTime;

    /** true=用户自选座不可用已降级自动分配（下单响应提示用） */
    private boolean degraded;
}
