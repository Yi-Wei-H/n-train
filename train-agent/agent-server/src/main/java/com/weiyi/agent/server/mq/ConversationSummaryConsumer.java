package com.weiyi.agent.server.mq;

import com.weiyi.agent.server.constant.AgentMqConst;
import com.weiyi.agent.server.service.chat.ConversationSummaryService;
import com.weiyi.agent.server.service.memory.MemoryTaskOutboxService;
import com.weiyi.common.core.util.JacksonUtil;
import com.weiyi.common.mq.core.TraceIdMqListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

/**
 * 会话摘要消费者：纯文本压缩任务，水位 CAS 保证不回退，失败保留旧摘要。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = AgentMqConst.TOPIC_CONVERSATION_SUMMARY,
        consumerGroup = AgentMqConst.GROUP_CONVERSATION_SUMMARY)
public class ConversationSummaryConsumer extends TraceIdMqListener {

    private static final int MAX_RECONSUME = 3;

    private final ConversationSummaryService summaryService;
    private final MemoryTaskOutboxService outboxService;

    @Override
    protected void handle(MessageExt message) {
        MemoryTaskEvent event = JacksonUtil.fromJson(bodyOf(message), MemoryTaskEvent.class);
        // 幂等：Outbox 行已终态（重复投递）直接跳过
        if (!outboxService.isConsumable(event.getOutboxId())) {
            log.info("[mq] 摘要任务 {} 已终态，跳过重复投递", event.getRequestId());
            return;
        }
        try {
            summaryService.summarize(event.getUserId(), event.getConversationId(), event.getMessageId());
            outboxService.markConsumed(event.getOutboxId());
        } catch (Exception e) {
            if (message.getReconsumeTimes() >= MAX_RECONSUME) {
                log.warn("[mq] 摘要任务 {} 连续 {} 次失败，置 FAILED：outboxId={}",
                        event.getRequestId(), MAX_RECONSUME, event.getOutboxId());
                outboxService.markFailed(event.getOutboxId(), e.getMessage());
                return;
            }
            throw e;
        }
    }
}
