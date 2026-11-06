package com.weiyi.common.mq.core;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局事务消息监听器：broker 回调 executeLocalTransaction/checkLocalTransaction 的唯一入口。
 *
 * <p>同一 JVM 内所有事务消息共用一个生产者组（rocketmq.producer.group），
 * 按 destination（topic:tag）路由到最近一次注册的 {@link TransactionalAction}——
 * 同一 destination 的事件语义相同，后注册覆盖先注册，回查时取到的即是正确的业务反查逻辑。
 */
@Slf4j
@RocketMQTransactionListener
public class MqTxListener implements RocketMQLocalTransactionListener {

    /**
     * arg 从未离开过这次调用的线程栈，不会发到 broker。
     * destination → 业务动作（回查时 arg 不回传，靠这里找回）
     */
    private final Map<String, TransactionalAction> actions = new ConcurrentHashMap<>();

    void register(String destination, TransactionalAction action) {
        actions.put(destination, action);
    }

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        if (arg instanceof TransactionalAction action) {
            try {
                action.executeLocal();
                return RocketMQLocalTransactionState.COMMIT;
            } catch (Exception e) {
                log.error("[mq-tx] 本地事务失败，回滚半消息 {}: {}", destination(msg), e.getMessage(), e);
                return RocketMQLocalTransactionState.ROLLBACK;
            }
        }
        log.warn("[mq-tx] 未知的事务消息 arg 类型: {}", arg);
        return RocketMQLocalTransactionState.ROLLBACK;
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        TransactionalAction action = actions.get(destination(msg));
        if (action == null) {
            // 服务重启后注册表为空（内存态），等业务下次发送前 broker 会继续回查
            log.warn("[mq-tx] 事务回查找不到已注册动作 {}: {}", destination(msg), msg.getHeaders());
            return RocketMQLocalTransactionState.UNKNOWN;
        }
        RocketMQLocalTransactionState state = action.checkLocal(msg);
        log.info("[mq-tx] 事务回查 {} → {}", destination(msg), state);
        return state;
    }

    private String destination(Message<?> msg) {
        Object topic = msg.getHeaders().get("rocketmq_TOPIC");
        Object tag = msg.getHeaders().get("rocketmq_TAGS");
        return topic == null ? "unknown" : topic + (tag == null ? "" : ":" + tag);
    }
}
