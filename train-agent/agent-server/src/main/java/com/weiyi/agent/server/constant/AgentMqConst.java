package com.weiyi.agent.server.constant;

/**
 * agent 域 MQ 主题/消费组（域内自产自销，不进 common-mq 的 MqConst）。
 * 命名沿用 MqConst 约定：Topic 前缀 T_、消费组前缀 G_。
 */
public final class AgentMqConst {

    /** 会话标题生成：chat 落库后投递，agent 自身消费（方案 C，主请求不等待标题任务） */
    public static final String TOPIC_CONVERSATION_TITLE = "T_CONVERSATION_TITLE";

    /** agent 标题生成消费组 */
    public static final String GROUP_CONVERSATION_TITLE = "G_CONVERSATION_TITLE";

    /** 长期记忆提炼：与消息落库同事务写 Outbox，Relay 投递，agent 自身消费 */
    public static final String TOPIC_MEMORY_EXTRACTION = "T_MEMORY_EXTRACTION";

    /** 记忆提炼消费组 */
    public static final String GROUP_MEMORY_EXTRACTION = "G_MEMORY_EXTRACTION";

    /** 会话摘要压缩：Outbox 投递，agent 自身消费 */
    public static final String TOPIC_CONVERSATION_SUMMARY = "T_CONVERSATION_SUMMARY";

    /** 会话摘要消费组 */
    public static final String GROUP_CONVERSATION_SUMMARY = "G_CONVERSATION_SUMMARY";

    private AgentMqConst() {
    }
}
