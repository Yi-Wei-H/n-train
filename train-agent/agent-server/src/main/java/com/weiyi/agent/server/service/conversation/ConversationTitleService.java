package com.weiyi.agent.server.service.conversation;
import com.weiyi.agent.server.service.AUsageLogService;
import com.weiyi.agent.server.service.AQuickCardService;
import com.weiyi.agent.server.service.model.AModelService;

import com.weiyi.agent.server.ai.provider.ChatTexts;
import com.weiyi.agent.server.ai.provider.LlmProviderRegistry;
import com.weiyi.agent.server.ai.safety.OutputSafety;
import com.weiyi.agent.server.ai.safety.ThinkTagStripper;
import com.weiyi.agent.server.ai.prompt.PromptAssembler;
import com.weiyi.agent.server.constant.PromptScene;
import com.weiyi.agent.server.ai.prompt.PromptTemplates;
import com.weiyi.agent.server.ai.provider.TokenUsage;
import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.server.constant.AgentMqConst;
import com.weiyi.agent.server.entity.AConversation;
import com.weiyi.agent.server.entity.AMessage;
import com.weiyi.agent.server.entity.AQuickCard;
import com.weiyi.agent.server.mq.ConversationTitleEvent;
import com.weiyi.agent.server.req.AMessageReq;
import com.weiyi.agent.server.req.AUsageLogReq;
import com.weiyi.common.mq.core.MqSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 会话标题生成：会话创建后首轮即定标题（请求线程入口），消费端异步生成——
 * 主请求只返回回答；短输入直接命名（不经 MQ、不调模型），长输入投递事件携带首条输入快照，
 * 消费不依赖本轮消息落库。系统触发的调用记为额外一轮消息（role=system，历史读取默认排除）。
 * 一次性投递：MQ 发送失败保留占位，可走【重新生成标题】手动补救，无自愈补投。
 */
@Slf4j
@Service
public class ConversationTitleService {

    /** 首条输入不超该长度直接以输入命名（清洗后定稿），节省一次模型调用 */
    private static final int DIRECT_TITLE_MAX_INPUT = 15;

    /** 标题长度上限（汉字），与标题模板约束一致 */
    private static final int TITLE_MAX_LENGTH = 20;

    private final AConversationService conversationService;
    private final AMessageService messageService;
    private final AModelService modelService;
    private final AUsageLogService usageLogService;
    private final AQuickCardService quickCardService;
    private final LlmProviderRegistry llmProviderRegistry;
    private final PromptTemplates promptTemplates;
    private final PromptAssembler promptAssembler;
    private final MqSender mqSender;

    public ConversationTitleService(AConversationService conversationService,
                                    AMessageService messageService,
                                    AModelService modelService,
                                    AUsageLogService usageLogService,
                                    AQuickCardService quickCardService,
                                    LlmProviderRegistry llmProviderRegistry,
                                    PromptTemplates promptTemplates,
                                    PromptAssembler promptAssembler,
                                    MqSender mqSender) {
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.modelService = modelService;
        this.usageLogService = usageLogService;
        this.quickCardService = quickCardService;
        this.llmProviderRegistry = llmProviderRegistry;
        this.promptTemplates = promptTemplates;
        this.promptAssembler = promptAssembler;
        this.mqSender = mqSender;
    }

    /**
     * 会话创建后调用（首轮，请求线程）：
     * 首条输入不超 15 字符时清洗后直接定稿（CAS 占位更新，meta 事件即带正式标题）；
     * 否则置 GENERATING 并投递 MQ，事件携带首条输入快照——消费不依赖本轮消息落库。
     * 短输入清洗后为空（纯符号等）保持占位；投递失败仅告警，不影响主流程。
     *
     * @return 标题是否已在请求线程处理完毕（直落定稿或保持占位）——为 true 时无需流内探测
     */
    public boolean onFirstRound(Long conversationId, String firstInput) {
        String trimmed = firstInput == null ? "" : firstInput.strip();
        if (trimmed.length() <= DIRECT_TITLE_MAX_INPUT) {
            String title = OutputSafety.cleanTitle(trimmed, TITLE_MAX_LENGTH);
            if (title.isEmpty()) {
                return true;
            }
            boolean updated = conversationService.updateTitleIfPlaceholder(conversationId, title);
            log.info("会话 {} 标题直落：{}（生效={}）", conversationId, title, updated);
            return true;
        }
        conversationService.markTitleGenerating(conversationId);
        ConversationTitleEvent event = new ConversationTitleEvent();
        event.setConversationId(conversationId);
        event.setFirstInput(trimmed);
        mqSender.sendQuietly(AgentMqConst.TOPIC_CONVERSATION_TITLE, null,
                String.valueOf(conversationId), event);
        return false;
    }

    /** 标题任务最终失败（消费重试超限）：保留占位并置 FAILED，此后不再补发 */
    public void markTitleFailed(Long conversationId) {
        conversationService.markTitleFailed(conversationId);
    }

    /** MQ 消费入口：素材取自事件携带的首条输入快照；标题已生成（重复消费）时静默跳过，CAS 保证不覆盖已有标题 */
    public void generate(Long conversationId, String firstInput) {
        AConversation conversation = conversationService.getById(conversationId);
        if (!AgentConstants.CONVERSATION_TITLE_PLACEHOLDER.equals(conversation.getTitle())) {
            log.info("[mq] 会话 {} 标题已生成，跳过", conversationId);
            return;
        }
        doGenerate(conversation, firstInput, false);
    }

    /** 重新生成标题（同步接口）：素材自行落库回查，直接覆盖、不受占位值限制 */
    public String regenerate(Long conversationId) {
        AConversation conversation = conversationService.getById(conversationId);
        return doGenerate(conversation, firstInputSnapshot(conversationId), true);
    }

    private String doGenerate(AConversation conversation, String firstInput, boolean overwrite) {
        Long modelId = conversation.getChatModelId() != null
                ? conversation.getChatModelId() : modelService.getCurrentModel().getId();
        long start = System.currentTimeMillis();
        String title;
        TokenUsage tokens;
        try {
            // 标题属附属任务，不追加业务安全常量，但反注入常量不可省略
            String systemPrompt = promptAssembler.secureSystem(
                    promptTemplates.system(PromptScene.TITLE_NAMING).render(), false);
            String userPrompt = promptTemplates.user(PromptScene.TITLE_NAMING).render(Map.of(
                    "firstInput", promptAssembler.wrap("input", firstInput)));
            ChatResponse response = llmProviderRegistry.getChatClient(modelId).prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call().chatResponse();
            title = ChatTexts.text(response);
            tokens = TokenUsage.of(response.getMetadata().getUsage());
        } catch (Exception e) {
            // 失败只记用量流水，不落 system 轮消息（历史轮次只记有结果的系统调用）
            recordUsage(conversation, modelId, System.currentTimeMillis() - start,
                    AgentConstants.CALL_STATUS_FAIL, e.getMessage(), null);
            throw e;
        }
        Long usageLogId = recordUsage(conversation, modelId, System.currentTimeMillis() - start,
                AgentConstants.CALL_STATUS_SUCCESS, null, tokens);
        // 后处理：去围栏引号换行与控制字符、隐私脱敏、超长截断（占位“新会话”不走模型，无此后处理）
        title = OutputSafety.cleanTitle(ThinkTagStripper.stripLeading(title), TITLE_MAX_LENGTH);
        if (title.isEmpty()) {
            log.warn("会话 {} 标题生成为空，保持占位待下轮自愈", conversation.getId());
            return conversation.getTitle();
        }
        saveSystemRound(conversation.getId(), title, modelId, usageLogId);
        boolean updated = overwrite
                ? conversationService.updateTitle(conversation.getId(), title)
                : conversationService.updateTitleIfPlaceholder(conversation.getId(), title);
        log.info("会话 {} 标题更新：{}（overwrite={} 生效={}）", conversation.getId(), title, overwrite, updated);
        return title;
    }

    /** 重新生成接口的素材：落库回查首条用户输入快照——卡片轮取用户可见卡片名（卡片已删回退原文），文本轮取原文 */
    private String firstInputSnapshot(Long conversationId) {
        AMessage firstRound = messageService.firstUserRound(conversationId);
        if (firstRound == null) {
            return "";
        }
        if (firstRound.getCardId() != null) {
            AQuickCard card = quickCardService.getById(firstRound.getCardId());
            if (card != null && card.getCardName() != null && !card.getCardName().isBlank()) {
                return card.getCardName();
            }
        }
        return firstRound.modelContent() == null ? "" : firstRound.modelContent();
    }

    /** 系统触发的调用记为额外一轮：role=system、reply=标题，关联本轮用量 */
    private void saveSystemRound(Long conversationId, String title, Long modelId, Long usageLogId) {
        AMessageReq req = new AMessageReq();
        req.setConversationId(conversationId);
        req.setRole(AgentConstants.MESSAGE_ROLE_SYSTEM);
        req.setReply(title);
        req.setChatModelId(modelId);
        req.setUsageLogId(usageLogId);
        messageService.create(req);
    }

    /** 标题用量是附属任务：记录失败仅告警，不反过来影响标题流程 */
    private Long recordUsage(AConversation conversation, Long modelId, long latencyMs,
                             int callStatus, String errMsg, TokenUsage tokens) {
        try {
            AUsageLogReq req = new AUsageLogReq();
            req.setUserId(conversation.getUserId());
            req.setModelId(modelId);
            req.setConversationId(conversation.getId());
            req.setLatencyMs((int) latencyMs);
            req.setCallStatus(callStatus);
            req.setErrMsg(errMsg);
            if (tokens != null) {
                req.setInputTokens(tokens.input());
                req.setCachedTokens(tokens.cached());
                req.setOutputTokens(tokens.output());
            }
            return usageLogService.record(req);
        } catch (Exception e) {
            log.warn("标题用量流水记录失败（已忽略）：conversationId={}: {}", conversation.getId(), e.getMessage());
            return null;
        }
    }
}
