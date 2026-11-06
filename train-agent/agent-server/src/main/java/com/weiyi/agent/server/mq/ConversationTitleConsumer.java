package com.weiyi.agent.server.mq;

import com.weiyi.agent.server.constant.AgentMqConst;
import com.weiyi.agent.server.service.conversation.ConversationTitleService;
import com.weiyi.common.core.util.JacksonUtil;
import com.weiyi.common.mq.core.TraceIdMqListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.springframework.stereotype.Component;

/**
 * 会话标题生成消费者（方案 C）：会话创建后首轮投递、此处异步生成，主请求不等待标题任务。
 * 幂等：title 已不是占位值即跳过，落库走 CAS 不覆盖已有标题。
 * 一次性投递：消费失败重试超限置 FAILED 保留占位，可走【重新生成标题】手动补救。
 * 消费 MessageExt 以便基类从用户属性恢复投递线程的 traceId。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = AgentMqConst.TOPIC_CONVERSATION_TITLE,
        consumerGroup = AgentMqConst.GROUP_CONVERSATION_TITLE)
public class ConversationTitleConsumer extends TraceIdMqListener {

    /** 消费重试上限：达到后放弃（自愈兜底覆盖，无需死信人工处理） */
    private static final int MAX_RECONSUME = 3;

    private final ConversationTitleService conversationTitleService;

    @Override
    protected void handle(MessageExt message) {
        ConversationTitleEvent event = JacksonUtil.fromJson(bodyOf(message), ConversationTitleEvent.class);
        try {
            conversationTitleService.generate(event.getConversationId(), event.getFirstInput());
        } catch (Exception e) {
            if (message.getReconsumeTimes() >= MAX_RECONSUME) {
                log.warn("[mq] 会话 {} 标题生成连续 {} 次失败，放弃（可手动重新生成）：{}",
                        event.getConversationId(), MAX_RECONSUME, e.getMessage());
                // 最终失败落 FAILED：保留占位标题，后续轮次不再自愈补发
                conversationTitleService.markTitleFailed(event.getConversationId());
                return;
            }
            throw e; // 未达上限，抛出触发 broker 重投
        }
    }
}
