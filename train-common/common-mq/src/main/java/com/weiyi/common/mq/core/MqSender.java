package com.weiyi.common.mq.core;

import com.weiyi.common.core.constant.Constants;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.trace.TraceIdSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * RocketMQ 统一发送入口：普通/延迟/事务三种消息，
 * 消息体一律 JSON 序列化（消费端按事件类反序列化），keys 传业务单号便于控制台排查。
 *
 * <p>用法约定：
 * <ul>
 *   <li>普通事件（丢了可容忍，有兜底扫描）：{@link #send}</li>
 *   <li>延迟触发（超时关单）：{@link #sendDelay}，档位见 {@link MqConst}</li>
 *   <li>本地事务 + 事件必须同时生效（关单/退款确认）：{@link #sendInTransaction}</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class MqSender {

    private static final long DEFAULT_TIMEOUT_MS = 3000;

    private final RocketMQTemplate template;
    private final MqTxListener txListener;

    /** 同步发送普通消息 */
    public SendResult send(String topic, String tag, String key, Object payload) {
        String dest = destination(topic, tag);
        SendResult result = template.syncSend(dest, buildMessage(key, payload), DEFAULT_TIMEOUT_MS);
        log.debug("[mq] 发送 {} key={} msgId={}", dest, key, result.getMsgId());
        return result;
    }

    /** 同步发送延迟消息（固定档位，见 MqConst 注释） */
    public SendResult sendDelay(String topic, String tag, String key, Object payload, int delayLevel) {
        String dest = destination(topic, tag);
        SendResult result = template.syncSend(dest, buildMessage(key, payload), DEFAULT_TIMEOUT_MS, delayLevel);
        log.info("[mq] 延迟发送 {} key={} level={} msgId={}", dest, key, delayLevel, result.getMsgId());
        return result;
    }

    /**
     * 事务消息：半消息 → action.executeLocal() 本地事务 → COMMIT/ROLLBACK。
     * 发送失败（半消息都没进 broker）直接抛异常；本地事务失败不抛异常，
     * 以返回值 localTransactionState=ROLLBACK 体现，调用方按需检查。
     */
    public TransactionSendResult sendInTransaction(String topic, String tag, String key, Object payload,
                                                   TransactionalAction action) {
        String dest = destination(topic, tag);
        txListener.register(dest, action);
        Message<?> message = buildMessage(key, payload);
        TransactionSendResult result = template.sendMessageInTransaction(dest, message, action);
        log.info("[mq] 事务发送 {} key={} state={}", dest, key, result.getLocalTransactionState());
        return result;
    }

    /** 发送事件；发送失败不阻断业务（如日志类事件），仅记录告警 */
    public void sendQuietly(String topic, String tag, String key, Object payload) {
        try {
            send(topic, tag, key, payload);
        } catch (Exception e) {
            log.warn("[mq] 发送失败（已忽略）{} key={}: {}", destination(topic, tag), key, e.getMessage());
        }
    }

    /** 延迟发送；失败不阻断业务（超时关单另有 Quartz 兜底扫描），仅记录告警 */
    public void sendDelayQuietly(String topic, String tag, String key, Object payload, int delayLevel) {
        try {
            sendDelay(topic, tag, key, payload, delayLevel);
        } catch (Exception e) {
            log.warn("[mq] 延迟发送失败（已忽略，依赖兜底扫描）{} key={}: {}",
                    destination(topic, tag), key, e.getMessage());
        }
    }

    private Message<?> buildMessage(String key, Object payload) {
        MessageBuilder<Object> builder = MessageBuilder.withPayload(payload)
                .setHeader(RocketMQHeaders.KEYS, key);
        // 发送线程 MDC 有 traceId 才写入消息用户属性，MQ 消费端据此恢复同一个 traceId（规则 5）
        try {
            String traceId = TraceIdSupport.current();
            if (traceId != null && !traceId.isBlank()) {
                builder.setHeader(Constants.HEADER_TRACE_ID, traceId);
            }
        } catch (Exception e) {
            log.warn("[trace] MQ 发送写入 traceId 失败，本次消息不带标记: {}", e.getMessage());
        }
        return builder.build();
    }

    private String destination(String topic, String tag) {
        return tag == null ? topic : topic + ":" + tag;
    }

    /** 兜底：模板缺失（未配置 rocketmq.name-server）时快速失败，避免静默丢消息 */
    public static MqSender require(MqSender sender) {
        if (sender == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "MQ 未配置");
        }
        return sender;
    }
}
