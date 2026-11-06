package com.weiyi.agent.server.service.memory;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.server.entity.AMemoryTaskOutbox;
import com.weiyi.agent.server.mapper.AMemoryTaskOutboxMapper;
import com.weiyi.common.core.util.JacksonUtil;
import com.weiyi.agent.server.mq.MemoryTaskEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 记忆任务 Outbox：入队与消息落库同事务（不丢任务）；Relay 投递失败退避重试；消费幂等回写。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryTaskOutboxService {

    /** 投递最大尝试次数，超过置 FAILED（任务积压告警人工介入） */
    private static final int MAX_ATTEMPTS = 5;

    private final AMemoryTaskOutboxMapper outboxMapper;

    /** 入队（调用方事务内）：requestId=本轮消息 id 前缀区分任务类型，唯一键防重复投递 */
    public void enqueue(Long userId, Long conversationId, Long messageId, String taskType, Integer memoryEpoch) {
        String requestId = (AgentConstants.MEMORY_TASK_SUMMARY.equals(taskType) ? "S-" : "E-") + messageId;
        AMemoryTaskOutbox task = new AMemoryTaskOutbox();
        task.setUserId(userId);
        task.setConversationId(conversationId);
        task.setRequestId(requestId);
        task.setTaskType(taskType);
        task.setMemoryEpoch(memoryEpoch);
        task.setPayload(JacksonUtil.toJson(eventOf(null, userId, conversationId, messageId, taskType, memoryEpoch)));
        task.setStatus(AgentConstants.MEMORY_TASK_PENDING);
        task.setAttempts(0);
        outboxMapper.insert(task);
    }

    /** 扫描待投递任务（到期或未设置重试时间的优先，限量） */
    public List<AMemoryTaskOutbox> scanPending(int limit) {
        return outboxMapper.selectPage(new Page<>(1, limit),
                        Wrappers.<AMemoryTaskOutbox>lambdaQuery()
                                .eq(AMemoryTaskOutbox::getStatus, AgentConstants.MEMORY_TASK_PENDING)
                                .and(w -> w.isNull(AMemoryTaskOutbox::getNextRetryAt)
                                        .or().le(AMemoryTaskOutbox::getNextRetryAt, LocalDateTime.now()))
                                .orderByAsc(AMemoryTaskOutbox::getId))
                .getRecords();
    }

    /** 投递成功：PENDING → SENT（CAS；消费端可能已抢先置 CONSUMED，不能覆盖终态） */
    public void markSent(Long outboxId) {
        outboxMapper.update(null, Wrappers.<AMemoryTaskOutbox>lambdaUpdate()
                .eq(AMemoryTaskOutbox::getId, outboxId)
                .eq(AMemoryTaskOutbox::getStatus, AgentConstants.MEMORY_TASK_PENDING)
                .set(AMemoryTaskOutbox::getStatus, AgentConstants.MEMORY_TASK_SENT)
                .set(AMemoryTaskOutbox::getUpdatedAt, LocalDateTime.now()));
    }

    /** 投递失败：attempts+1 并退避，超限置 FAILED（CAS 于 PENDING，不把已消费的行重新拉回待投递） */
    public void markSendFailed(Long outboxId, String reason) {
        AMemoryTaskOutbox task = outboxMapper.selectById(outboxId);
        if (task == null || task.getStatus() != AgentConstants.MEMORY_TASK_PENDING) {
            return;
        }
        int attempts = task.getAttempts() + 1;
        boolean giveUp = attempts >= MAX_ATTEMPTS;
        // 退避：1min 起 ×2，封顶 30min
        long backoffMinutes = Math.min(30L, 1L << Math.min(attempts, 5));
        if (giveUp) {
            log.error("[memory-outbox] 任务 {} 投递 {} 次仍失败，置 FAILED：{}", outboxId, MAX_ATTEMPTS, reason);
        } else {
            log.warn("[memory-outbox] 任务 {} 投递失败（第 {} 次），{} 分钟后重试：{}",
                    outboxId, attempts, backoffMinutes, reason);
        }
        outboxMapper.update(null, Wrappers.<AMemoryTaskOutbox>lambdaUpdate()
                .eq(AMemoryTaskOutbox::getId, outboxId)
                .eq(AMemoryTaskOutbox::getStatus, AgentConstants.MEMORY_TASK_PENDING)
                .set(AMemoryTaskOutbox::getAttempts, attempts)
                .set(AMemoryTaskOutbox::getStatus, giveUp ? AgentConstants.MEMORY_TASK_FAILED : AgentConstants.MEMORY_TASK_PENDING)
                .set(AMemoryTaskOutbox::getNextRetryAt, giveUp ? null : LocalDateTime.now().plusMinutes(backoffMinutes))
                .set(AMemoryTaskOutbox::getUpdatedAt, LocalDateTime.now()));
    }

    /**
     * 消费前置检查：PENDING 与 SENT 都可消费——Relay 是「先发消息、后回写 SENT」，
     * 消费端可能先于状态回写收到消息，只认 SENT 会把这类消息当重复投递丢弃（任务永远停在 SENT）。
     * CONSUMED/FAILED 为终态，重复投递直接跳过。
     */
    public boolean isConsumable(Long outboxId) {
        AMemoryTaskOutbox task = outboxMapper.selectById(outboxId);
        return task != null && isOpen(task);
    }

    /** 消费完成：PENDING/SENT → CONSUMED（CAS，已终态跳过） */
    public void markConsumed(Long outboxId) {
        consume(outboxId, AgentConstants.MEMORY_TASK_CONSUMED, null);
    }

    /** 消费最终失败：PENDING/SENT → FAILED（broker 重投达上限后） */
    public void markFailed(Long outboxId, String reason) {
        consume(outboxId, AgentConstants.MEMORY_TASK_FAILED, reason);
    }

    private void consume(Long outboxId, int status, String reason) {
        int updated = outboxMapper.update(null, Wrappers.<AMemoryTaskOutbox>lambdaUpdate()
                .eq(AMemoryTaskOutbox::getId, outboxId)
                .in(AMemoryTaskOutbox::getStatus,
                        AgentConstants.MEMORY_TASK_PENDING, AgentConstants.MEMORY_TASK_SENT)
                .set(AMemoryTaskOutbox::getStatus, status)
                .set(AMemoryTaskOutbox::getUpdatedAt, LocalDateTime.now()));
        if (reason != null && updated > 0) {
            log.error("[memory-outbox] 任务 {} 消费最终失败：{}", outboxId, reason);
        }
    }

    private boolean isOpen(AMemoryTaskOutbox task) {
        return task.getStatus() == AgentConstants.MEMORY_TASK_PENDING
                || task.getStatus() == AgentConstants.MEMORY_TASK_SENT;
    }

    /** 组装投递事件（outboxId 由 Relay 补齐） */
    public MemoryTaskEvent eventOf(Long outboxId, Long userId, Long conversationId,
                                   Long messageId, String taskType, Integer memoryEpoch) {
        MemoryTaskEvent event = new MemoryTaskEvent();
        event.setOutboxId(outboxId);
        event.setRequestId((AgentConstants.MEMORY_TASK_SUMMARY.equals(taskType) ? "S-" : "E-") + messageId);
        event.setUserId(userId);
        event.setConversationId(conversationId);
        event.setMessageId(messageId);
        event.setTaskType(taskType);
        event.setMemoryEpoch(memoryEpoch);
        return event;
    }
}
