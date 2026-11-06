package com.weiyi.common.mq.core;

import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;

/**
 * 事务消息的本地动作：半消息发送成功后由 {@link MqTxListener} 回调。
 *
 * <p>执行顺序：broker 先收到半消息（对消费者不可见）→ {@link #executeLocal()}
 * 成功则 COMMIT 投递、异常则 ROLLBACK 丢弃；若 producer 宕机未返回结果，
 * broker 会定期回查 {@link #checkLocal(Message)}（按业务状态反查，不是重放本地事务）。
 *
 * <p>实现要求：executeLocal 内做本地事务写库；checkLocal 按消息反查落库结果——
 * 已生效返回 COMMIT，明确未生效返回 ROLLBACK，暂时无法判断返回 UNKNOWN 等 broker 下次回查。
 */
public interface TransactionalAction {

    /** 本地事务（仅成功或抛异常两种结局，内部不要吞异常） */
    void executeLocal() throws Exception;

    /** 事务回查：按消息（key/消息体）反查本地事务是否已生效 */
    RocketMQLocalTransactionState checkLocal(Message<?> message);
}
