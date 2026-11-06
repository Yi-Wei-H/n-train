package com.weiyi.common.mq.event;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/**
 * 退款批次确认事件（事务消息投递）：钱包已入账、订单明细已转已退之后发出。
 * 列车域按明细释放对应座位占用并回补余票；会员域失效余票缓存。
 */
@Data
public class OrderRefundedEvent implements Serializable {

    private String batchNo;

    private Long orderId;

    private String orderNo;

    /** 本批次退掉的订单明细（列车域按 order_item_id 定位占用记录） */
    private List<Long> orderItemIds;

    private LocalDate rideDate;

    private String fromStation;

    private String toStation;
}
