package com.weiyi.agent.server.service.chat;
import com.weiyi.agent.server.service.memory.MemoryTaskOutboxService;
import com.weiyi.agent.server.service.conversation.AMessageService;
import com.weiyi.agent.server.service.memory.MemoryService;
import com.weiyi.agent.server.service.conversation.AConversationService;

import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.server.constant.AgentMqConst;
import com.weiyi.agent.server.entity.AConversation;
import com.weiyi.agent.server.entity.AUserMemorySetting;
import com.weiyi.agent.server.req.AMessageReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 聊天轮次事务落库：整轮消息 + 会话聚合 + 记忆任务 Outbox 在同一本地事务提交（消息不丢任务也不丢）。
 * 提炼任务仅在总开关开启时入队（携带当时纪元）；摘要任务按轮数阈值周期性入队。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatRoundPersistService {

    private final AMessageService messageService;
    private final AConversationService conversationService;
    private final MemoryService memoryService;
    private final MemoryTaskOutboxService outboxService;
    private final com.weiyi.agent.server.config.AgentProperties agentProperties;

    /**
     * 落一轮消息并按需入队异步任务，返回本轮消息 id。
     * userContent 为展示原文，userContentMasked 为喂模型净化版（空=回退原文）；
     * reply 为空=失败轮（用户输入保留）；usageLogId 可空（无模型调用的固定回复轮）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long saveRoundWithTasks(Long conversationId, Long userId, Long cardId, String userContent,
                                   String userContentMasked, String reply, Long modelId, Long usageLogId) {
        AMessageReq req = new AMessageReq();
        req.setConversationId(conversationId);
        req.setRole(AgentConstants.MESSAGE_ROLE_USER);
        req.setUserContent(userContent);
        req.setUserContentMasked(userContentMasked);
        req.setCardId(cardId);
        req.setReply(reply);
        req.setChatModelId(modelId);
        req.setUsageLogId(usageLogId);
        Long messageId = messageService.create(req);

        // 会话聚合：轮数 +1（摘要任务的触发依据）
        int messageCount = conversationService.incrementMessageCount(conversationId);

        // 长期记忆提炼：有回复的轮次且开关开启时入队，携带入队时刻纪元（消费前比对防复活）
        if (reply != null && !reply.isBlank() && memoryService.enabled(userId)) {
            AUserMemorySetting setting = memoryService.settingOf(userId);
            outboxService.enqueue(userId, conversationId, messageId,
                    AgentConstants.MEMORY_TASK_EXTRACTION, setting.getMemoryEpoch());
        }

        // 会话摘要：每达阈值轮入队一次（周期性压缩，水位由消费端校验不后退）
        int threshold = agentProperties.getMemory().getSummaryThresholdRounds();
        if (messageCount >= threshold && messageCount % threshold == 0) {
            outboxService.enqueue(userId, conversationId, messageId,
                    AgentConstants.MEMORY_TASK_SUMMARY, null);
        }
        return messageId;
    }

    /**
     * 只落消息与轮数聚合，不入队任何异步任务：错误轮专用——
     * 固定错误文案不是模型产出，不应被提炼成长期记忆，也不参与摘要素材语义。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long saveRoundOnly(Long conversationId, Long userId, Long cardId, String userContent,
                              String userContentMasked, String reply, Long modelId, Long usageLogId) {
        AMessageReq req = new AMessageReq();
        req.setConversationId(conversationId);
        req.setRole(AgentConstants.MESSAGE_ROLE_USER);
        req.setUserContent(userContent);
        req.setUserContentMasked(userContentMasked);
        req.setCardId(cardId);
        req.setReply(reply);
        req.setChatModelId(modelId);
        req.setUsageLogId(usageLogId);
        Long messageId = messageService.create(req);
        conversationService.incrementMessageCount(conversationId);
        return messageId;
    }

    /** 会话当前摘要（无则为"无"，注入 user 模板槽位） */
    public String summaryOf(Long conversationId) {
        AConversation conversation = conversationService.getById(conversationId);
        return conversation != null && conversation.getSummary() != null
                && !conversation.getSummary().isBlank() ? conversation.getSummary() : "无";
    }
}
