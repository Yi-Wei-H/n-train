package com.weiyi.agent.server.mq;

import lombok.Data;

import java.io.Serializable;

/**
 * 记忆任务事件（Outbox → MQ）：只携带最小定位数据，消费端按 userId+conversationId 回查已落库内容。
 */
@Data
public class MemoryTaskEvent implements Serializable {

    /** Outbox 行 id（消费完成回写状态的定位键） */
    private Long outboxId;

    /** 任务请求标识（Outbox 唯一键成分，日志定位用） */
    private String requestId;

    private Long userId;

    private Long conversationId;

    /** 触发任务的本轮消息 id */
    private Long messageId;

    /** 任务类型：MEMORY_EXTRACTION / CONVERSATION_SUMMARY */
    private String taskType;

    /** 入队时记忆纪元（仅提炼任务携带） */
    private Integer memoryEpoch;
}
