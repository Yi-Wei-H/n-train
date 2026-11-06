package com.weiyi.agent.server.mq;

import com.weiyi.agent.server.constant.AgentMqConst;
import com.weiyi.agent.server.service.memory.MemoryExtractionService;
import com.weiyi.agent.server.service.memory.MemoryTaskOutboxService;
import com.weiyi.common.core.util.JacksonUtil;
import com.weiyi.common.mq.core.TraceIdMqListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

/**
 * 记忆提炼消费者：Outbox Relay 投递，此处回查消息并提炼。
 * 幂等：Outbox 行已 CONSUMED/FAILED 时跳过（Event 携带 outboxId）；broker 重投由 MQ 重试次数封顶。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = AgentMqConst.TOPIC_MEMORY_EXTRACTION,
        consumerGroup = AgentMqConst.GROUP_MEMORY_EXTRACTION)
public class MemoryExtractionConsumer extends TraceIdMqListener {

    /** 消费重试上限：达到后 Outbox 置 FAILED，放弃本批（不影响主回复） */
    private static final int MAX_RECONSUME = 3;

    private final MemoryExtractionService memoryExtractionService;
    private final MemoryTaskOutboxService outboxService;

    @Override
    protected void handle(MessageExt message) {
        MemoryTaskEvent event = JacksonUtil.fromJson(bodyOf(message), MemoryTaskEvent.class);
        // 幂等：Outbox 行已 CONSUMED/FAILED（重复投递）直接跳过，避免重复调模型与重复采纳
        if (!outboxService.isConsumable(event.getOutboxId())) {
            log.info("[mq] 提炼任务 {} 已终态，跳过重复投递", event.getRequestId());
            return;
        }
        try {
            memoryExtractionService.extract(event.getUserId(), event.getConversationId(),
                    event.getMessageId(), event.getMemoryEpoch());
            outboxService.markConsumed(event.getOutboxId());
        } catch (Exception e) {
            if (message.getReconsumeTimes() >= MAX_RECONSUME) {
                log.warn("[mq] 提炼任务 {} 连续 {} 次失败，置 FAILED：outboxId={}",
                        event.getRequestId(), MAX_RECONSUME, event.getOutboxId());
                outboxService.markFailed(event.getOutboxId(), e.getMessage());
                return;
            }
            throw e;
        }
    }
}
