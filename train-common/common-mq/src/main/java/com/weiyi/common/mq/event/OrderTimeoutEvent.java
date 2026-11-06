package com.weiyi.common.mq.event;

import lombok.Data;

import java.io.Serializable;

/**
 * 订单支付超时事件（延迟消息）：下单成功后投递，到期由订单域消费——
 * 仍为待支付则关单（再经事务消息广播 ORDER_CLOSED）。
 */
@Data
public class OrderTimeoutEvent implements Serializable {

    private Long orderId;
}
