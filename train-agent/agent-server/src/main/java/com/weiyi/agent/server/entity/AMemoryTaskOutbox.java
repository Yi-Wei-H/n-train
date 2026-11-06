package com.weiyi.agent.server.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 记忆任务 Outbox：主回复消息与任务行同本地事务提交，Relay 投递 MQ（失败退避重试），消费幂等。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("a_memory_task_outbox")
public class AMemoryTaskOutbox extends BaseTimeEntity {

    /** 账户，Consumer 使用并再次校验 */
    private Long userId;

    /** 来源会话 */
    private Long conversationId;

    /** 触发任务的本轮请求标识；与 task_type 组成唯一键防重复投递 */
    private String requestId;

    /** 任务类型：MEMORY_EXTRACTION / CONVERSATION_SUMMARY */
    private String taskType;

    /** 入队时记忆纪元（仅提炼任务），消费前比对防复活 */
    private Integer memoryEpoch;

    /** 最小任务数据 JSON（消息 id/摘要水位），消费端按 userId+conversationId 回查 */
    private String payload;

    /** 状态：0待投递 1已投递 2已消费 3失败 */
    private Integer status;

    /** 投递尝试次数 */
    private Integer attempts;

    /** 下次重试时间（退避） */
    private java.time.LocalDateTime nextRetryAt;
}
