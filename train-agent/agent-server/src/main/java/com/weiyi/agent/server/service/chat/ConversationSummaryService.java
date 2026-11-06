package com.weiyi.agent.server.service.chat;
import com.weiyi.agent.server.service.AUsageLogService;
import com.weiyi.agent.server.service.conversation.AMessageService;
import com.weiyi.agent.server.service.conversation.AConversationService;
import com.weiyi.agent.server.service.model.AModelService;

import com.weiyi.agent.server.ai.provider.ChatTexts;
import com.weiyi.agent.server.ai.provider.LlmProviderRegistry;
import com.weiyi.agent.server.ai.safety.OutputSafety;
import com.weiyi.agent.server.ai.prompt.PromptAssembler;
import com.weiyi.agent.server.constant.PromptScene;
import com.weiyi.agent.server.ai.prompt.PromptTemplates;
import com.weiyi.agent.server.config.AgentProperties;
import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.server.entity.AConversation;
import com.weiyi.agent.server.entity.AModel;
import com.weiyi.agent.server.req.AUsageLogReq;
import com.weiyi.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 会话摘要消费：纯文本任务（不走结构化转换器），旧摘要 + 近期消息 → 压缩摘要。
 * 水位 CAS 写入（只前进不回退）；输出做长度与脱敏检查；失败保留旧摘要，不影响聊天。
 * 摘要只服务当前会话上下文续接，不是长期记忆、不能作为写操作确认凭据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationSummaryService {

    private final AConversationService conversationService;
    private final AMessageService messageService;
    private final AModelService modelService;
    private final AUsageLogService usageLogService;
    private final LlmProviderRegistry llmProviderRegistry;
    private final PromptTemplates promptTemplates;
    private final PromptAssembler promptAssembler;
    private final AgentProperties agentProperties;

    public void summarize(Long userId, Long conversationId, Long watermarkMessageId) {
        AConversation conversation;
        try {
            conversation = conversationService.getById(conversationId);
        } catch (BusinessException e) {
            // 会话已删除：任务直接放弃（幂等落终态），不烧重试
            log.info("[summary] 会话 {} 不存在（可能已删除），跳过摘要", conversationId);
            return;
        }
        if (conversation.getSummaryMessageId() != null
                && watermarkMessageId <= conversation.getSummaryMessageId()) {
            log.info("[summary] 会话 {} 水位不前进，跳过摘要", conversationId);
            return;
        }
        String previousSummary = conversation.getSummary() == null || conversation.getSummary().isBlank()
                ? "无" : conversation.getSummary();
        // 摘要素材：旧摘要 + 覆盖窗口内消息（水位以来近两倍阈值轮，随阈值伸缩）
        String history = messageService.latestAsText(conversationId,
                agentProperties.getMemory().getSummaryThresholdRounds() * 2);

        AModel model = conversation.getChatModelId() != null
                ? modelService.getModelById(conversation.getChatModelId()) : modelService.getCurrentModel();
        long start = System.currentTimeMillis();
        try {
            String summary = ChatTexts.text(llmProviderRegistry.getChatClientWithoutSafeguard(model.getId()).prompt()
                    .system(promptAssembler.secureSystem(
                            promptTemplates.system(PromptScene.CONVERSATION_SUMMARY).render(), false))
                    .user(promptTemplates.user(PromptScene.CONVERSATION_SUMMARY).render(Map.of(
                            "previousSummary", promptAssembler.wrap("summary", previousSummary),
                            "historyMessages", promptAssembler.wrap("history", history))))
                    .call()
                    .chatResponse());
            String cleaned = summarizeText(summary);
            if (cleaned.isBlank()) {
                log.warn("[summary] 会话 {} 摘要生成为空，保留旧摘要", conversationId);
                return;
            }
            boolean updated = conversationService.updateSummaryIfNewer(conversationId, watermarkMessageId, cleaned);
            log.info("[summary] 会话 {} 摘要更新：watermark={}, 生效={}", conversationId, watermarkMessageId, updated);
            recordUsage(userId, model.getId(), conversationId,
                    System.currentTimeMillis() - start, AgentConstants.CALL_STATUS_SUCCESS, null);
        } catch (Exception e) {
            log.warn("[summary] 会话 {} 摘要生成失败，保留旧摘要：{}", conversationId, e.getMessage());
            recordUsage(userId, model.getId(), conversationId,
                    System.currentTimeMillis() - start, AgentConstants.CALL_STATUS_FAIL, e.getMessage());
        }
    }

    /** 摘要后处理：去首尾、脱敏（证件号/令牌/提示词回显）、长度截断 */
    private String summarizeText(String summary) {
        if (summary == null) {
            return "";
        }
        String cleaned = OutputSafety.scrub(summary.strip());
        int max = agentProperties.getMemory().getSummaryMaxLength();
        return cleaned.length() > max ? cleaned.substring(0, max) : cleaned;
    }

    private void recordUsage(Long userId, Long modelId, Long conversationId, long latencyMs,
                             int callStatus, String errMsg) {
        try {
            AUsageLogReq req = new AUsageLogReq();
            req.setUserId(userId);
            req.setModelId(modelId);
            req.setConversationId(conversationId);
            req.setLatencyMs((int) latencyMs);
            req.setCallStatus(callStatus);
            req.setErrMsg(errMsg);
            usageLogService.record(req);
        } catch (Exception e) {
            log.warn("[summary] 用量记录失败（已忽略）：{}", e.getMessage());
        }
    }
}
