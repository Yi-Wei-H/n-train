package com.weiyi.business.order.mq;

import com.weiyi.common.core.constant.StatusConsts;
import com.weiyi.common.core.util.JacksonUtil;
import com.weiyi.common.mq.core.MqConst;
import com.weiyi.common.mq.core.MqSender;
import com.weiyi.common.mq.core.TraceIdMqListener;
import com.weiyi.common.mq.event.OrderTimeoutEvent;
import com.weiyi.business.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 超时关单消费者：下单时投递的延迟消息到期触发。
 * 延迟档位只是近似触发点：消费时按订单实际支付截止时间（createdAt + pay-timeout-minutes）复判——
 * <ul>
 *   <li>已支付/已取消/不存在 → 静默跳过</li>
 *   <li>未到期（配置调整/时钟偏差/档位不精确）→ re-arm：按剩余时间补投覆盖档位延迟消息接力</li>
 *   <li>已到期仍待支付 → 关单（本地事务取消 + 释放座位 + 事务消息广播 ORDER_CLOSED）</li>
 * </ul>
 * 消息丢失/消费失败由 train-task 的 OrderCloseSweepJob 兜底扫描覆盖。
 * 消费 MessageExt 以便基类从用户属性恢复下单线程的 traceId。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = MqConst.TOPIC_ORDER_TIMEOUT,
        consumerGroup = MqConst.GROUP_ORDER_TIMEOUT)
public class OrderTimeoutConsumer extends TraceIdMqListener {

    private final OrderService orderService;
    private final MqSender mqSender;

    @Override
    protected void handle(MessageExt message) {
        OrderTimeoutEvent event = JacksonUtil.fromJson(bodyOf(message), OrderTimeoutEvent.class);
        long orderId = event.getOrderId();
        long remainMillis = orderService.remainingPayMillis(orderId);
        if (remainMillis == -1) {
            log.info("[mq] 订单 {} 到期检查：已支付或已取消，跳过", orderId);
            return;
        }
        if (remainMillis > 0) {
            int level = MqConst.delayLevelCovering(Duration.ofMillis(remainMillis));
            mqSender.sendDelayQuietly(MqConst.TOPIC_ORDER_TIMEOUT, null, String.valueOf(orderId),
                    event, level);
            log.info("[mq] 订单 {} 未到支付截止，re-arm 延迟消息（level={}，剩余 {}ms）", orderId, level, remainMillis);
            return;
        }
        boolean canceled = orderService.cancelIfUnpaid(orderId, StatusConsts.CANCEL_TIMEOUT);
        if (canceled) {
            log.info("[mq] 订单 {} 超时未支付，已自动关单并本地释放座位", orderId);
        } else {
            log.info("[mq] 订单 {} 到期检查：状态已变化，跳过", orderId);
        }
    }
}
