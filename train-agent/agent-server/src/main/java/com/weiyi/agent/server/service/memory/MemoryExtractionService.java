package com.weiyi.agent.server.service.memory;
import com.weiyi.agent.server.service.AUsageLogService;
import com.weiyi.agent.server.service.conversation.AMessageService;
import com.weiyi.agent.server.service.model.AModelService;

import com.weiyi.agent.server.ai.provider.LlmProviderRegistry;
import com.weiyi.agent.server.ai.prompt.PromptAssembler;
import com.weiyi.agent.server.constant.PromptScene;
import com.weiyi.agent.server.ai.prompt.PromptTemplates;
import com.weiyi.agent.server.ai.prompt.StructuredOutputInvoker;
import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.server.dto.MemoryExtractionResult;
import com.weiyi.agent.server.entity.AModel;
import com.weiyi.agent.server.entity.AMessage;
import com.weiyi.agent.server.req.AUsageLogReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 长期记忆提炼消费：回查本轮已落库消息 → 净化包裹 → 提炼模板 + 结构化调用 → 服务端二次校验采纳。
 * 消费端不继承请求线程上下文：traceId 由 MQ 基类恢复，DB 读写全部显式带 userId；
 * 纪元/开关不一致整批放弃；结构化失败降级空结果（不落半条候选），不影响已完成的主回复。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryExtractionService {

    private final AMessageService messageService;
    private final AModelService modelService;
    private final AUsageLogService usageLogService;
    private final LlmProviderRegistry llmProviderRegistry;
    private final PromptTemplates promptTemplates;
    private final PromptAssembler promptAssembler;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final MemoryService memoryService;

    public void extract(Long userId, Long conversationId, Long messageId, Integer epoch) {
        AMessage message = messageService.getById(messageId);
        if (message == null || message.getReply() == null || message.getReply().isBlank()) {
            log.info("[memory] 轮 {} 无回复或不存在，跳过提炼", messageId);
            return;
        }
        String roundContent = "用户：" + nullSafe(message.modelContent())
                + "\n助手：" + message.getReply();

        AModel model = modelService.getCurrentModel();
        long start = System.currentTimeMillis();
        MemoryExtractionResult result;
        try {
            BeanOutputConverter<MemoryExtractionResult> converter = new BeanOutputConverter<>(MemoryExtractionResult.class);
            String systemPrompt = promptAssembler.secureSystem(
                    promptTemplates.system(PromptScene.MEMORY_EXTRACTION).render(), false);
            String userPrompt = promptTemplates.user(PromptScene.MEMORY_EXTRACTION).render(Map.of(
                    "roundContent", promptAssembler.wrap("history", roundContent)));
            // 提炼喂本轮输入与回复文本，历史可能含敏感词：无 SafeGuard 客户端（入口守卫是输入防线）
            ChatClient client = llmProviderRegistry.getChatClientWithoutSafeguard(model.getId());
            result = structuredOutputInvoker.invoke(client, systemPrompt, userPrompt,
                    converter, MemoryExtractionResult.empty(), "memory-extraction");
            recordUsage(userId, model.getId(), conversationId,
                    System.currentTimeMillis() - start, AgentConstants.CALL_STATUS_SUCCESS, null);
        } catch (Exception e) {
            log.warn("[memory] 提炼调用失败：messageId={}: {}", messageId, e.getMessage());
            recordUsage(userId, model.getId(), conversationId,
                    System.currentTimeMillis() - start, AgentConstants.CALL_STATUS_FAIL, e.getMessage());
            return;
        }
        memoryService.acceptExtraction(userId, messageId, result.operations(), epoch);
        log.info("[memory] 提炼完成：messageId={}, 候选 {} 条", messageId,
                result.operations() == null ? 0 : result.operations().size());
    }

    private String nullSafe(String text) {
        return text == null ? "" : text;
    }

    /** 提炼任务独立用量流水（失败不阻断，只记流水），traceId 由 MQ 基类恢复 */
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
            log.warn("[memory] 提炼用量记录失败（已忽略）：{}", e.getMessage());
        }
    }
}
