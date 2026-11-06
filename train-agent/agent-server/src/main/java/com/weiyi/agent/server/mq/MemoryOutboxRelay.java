package com.weiyi.agent.server.mq;

import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.server.constant.AgentMqConst;
import com.weiyi.agent.server.entity.AMemoryTaskOutbox;
import com.weiyi.agent.server.service.memory.MemoryTaskOutboxService;
import com.weiyi.common.mq.core.MqSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 记忆任务 Outbox Relay：定时扫描 PENDING 投递 MQ，成功置 SENT，失败退避重试（不吞异常）。
 * 与消息落库同事务写入的 Outbox 保证任务不丢；进程重启后未投递任务继续投递。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryOutboxRelay {

    /** 单批投递上限 */
    private static final int BATCH_LIMIT = 50;

    private final MemoryTaskOutboxService outboxService;
    private final MqSender mqSender;

    @Scheduled(fixedDelay = 5000)
    public void relay() {
        List<AMemoryTaskOutbox> pending = outboxService.scanPending(BATCH_LIMIT);
        for (AMemoryTaskOutbox task : pending) {
            String topic = AgentConstants.MEMORY_TASK_SUMMARY.equals(task.getTaskType())
                    ? AgentMqConst.TOPIC_CONVERSATION_SUMMARY : AgentMqConst.TOPIC_MEMORY_EXTRACTION;
            try {
                // 不用 sendQuietly：投递异常必须感知并退避，静默吞掉会丢任务
                mqSender.send(topic, null, task.getRequestId(), outboxService.eventOf(
                        task.getId(), task.getUserId(), task.getConversationId(),
                        parseMessageId(task.getRequestId()), task.getTaskType(), task.getMemoryEpoch()));
                outboxService.markSent(task.getId());
            } catch (Exception e) {
                outboxService.markSendFailed(task.getId(), e.getMessage());
            }
        }
    }

    /** requestId 还原消息 id（E-xxx 提炼 / S-xxx 摘要） */
    private Long parseMessageId(String requestId) {
        return Long.valueOf(requestId.substring(requestId.indexOf('-') + 1));
    }
}
