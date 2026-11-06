package com.weiyi.common.mq.event;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 订单关闭事件（超时/手动取消/下单失败回滚，事务消息投递）。
 * 列车域按 orderNo 释放座位占用与余票；会员域失效余票缓存。
 */
@Data
public class OrderClosedEvent implements Serializable {

    private Long orderId;

    /** 锁座号 = 订单号，列车域幂等释放的关联键 */
    private String orderNo;

    private LocalDate rideDate;

    private String fromStation;

    private String toStation;
}
