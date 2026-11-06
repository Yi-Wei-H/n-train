package com.weiyi.member.mq;

import com.weiyi.common.core.util.JacksonUtil;
import com.weiyi.common.mq.core.MqConst;
import com.weiyi.common.mq.core.TraceIdMqListener;
import com.weiyi.common.mq.event.OrderClosedEvent;
import com.weiyi.common.mq.event.OrderRefundedEvent;
import com.weiyi.member.service.TicketBizService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

/**
 * 订单事件消费（会员域视角）：关单/退款 → 失效余票缓存。
 * 这些动作由订单域触发（超时关单走延迟消息），member 不在调用链上，
 * 靠 MQ 事件最终一致地刷新自己的缓存。
 * 继承 TraceIdMqListener：消费日志带上产生该事件的请求 traceId。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = MqConst.TOPIC_ORDER_EVENT,
        consumerGroup = MqConst.GROUP_MEMBER_ORDER,
        selectorExpression = MqConst.TAG_ORDER_CLOSED + " || " + MqConst.TAG_ORDER_REFUNDED)
public class OrderEventConsumer extends TraceIdMqListener {

    private final TicketBizService ticketBizService;

    @Override
    protected void handle(MessageExt message) {
        String tag = message.getTags();
        String body = bodyOf(message);
        switch (tag == null ? "" : tag) {
            case MqConst.TAG_ORDER_CLOSED -> {
                OrderClosedEvent event = JacksonUtil.fromJson(body, OrderClosedEvent.class);
                ticketBizService.evictTicketCache(event.getRideDate(), event.getFromStation(), event.getToStation());
                log.debug("[mq] 订单关闭，余票缓存已失效：{}", event.getOrderNo());
            }
            case MqConst.TAG_ORDER_REFUNDED -> {
                OrderRefundedEvent event = JacksonUtil.fromJson(body, OrderRefundedEvent.class);
                if (event.getRideDate() == null || event.getFromStation() == null || event.getToStation() == null) {
                    // 容忍缺失行程字段的事件体（历史缺陷消息）：跳过失效并 ack，避免毒消息无限重投
                    log.warn("[mq] 退款事件缺失行程字段，跳过余票缓存失效：batchNo={}", event.getBatchNo());
                    return;
                }
                ticketBizService.evictTicketCache(event.getRideDate(), event.getFromStation(), event.getToStation());
                log.debug("[mq] 退款完成，余票缓存已失效：{}", event.getOrderNo());
            }
            default -> log.warn("[mq] 未知的订单事件 tag={}", tag);
        }
    }
}
