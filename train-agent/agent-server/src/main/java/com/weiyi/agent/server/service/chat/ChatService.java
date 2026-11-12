package com.weiyi.agent.server.service.chat;
import com.weiyi.agent.server.service.AUsageLogService;
import com.weiyi.agent.server.service.AQuickCardService;
import com.weiyi.agent.server.service.conversation.AMessageService;
import com.weiyi.agent.server.service.memory.MemoryService;
import com.weiyi.agent.server.service.conversation.ConversationTitleService;
import com.weiyi.agent.server.service.conversation.AccountConversationService;
import com.weiyi.agent.server.service.model.AUserModelService;
import com.weiyi.agent.server.service.conversation.AConversationService;
import com.weiyi.agent.server.service.memory.MemoryCommandService;
import com.weiyi.agent.server.service.model.AModelService;

import com.weiyi.agent.server.ai.provider.ChatTexts;
import com.weiyi.agent.server.ai.provider.ErrorClassifier;
import com.weiyi.agent.server.ai.provider.LlmProviderRegistry;
import com.weiyi.agent.server.ai.safety.OutputSafety;
import com.weiyi.agent.server.ai.prompt.PromptAssembler;
import com.weiyi.agent.server.ai.prompt.PromptSanitizer;
import com.weiyi.agent.server.constant.PromptScene;
import com.weiyi.agent.server.ai.prompt.PromptTemplates;
import com.weiyi.agent.server.ai.safety.StreamScrubber;
import com.weiyi.agent.server.ai.safety.ThinkTagStripper;
import com.weiyi.agent.server.ai.provider.TokenUsage;
import com.weiyi.agent.server.ai.tool.OrderQueryTools;
import com.weiyi.agent.server.ai.tool.PassengerTools;
import com.weiyi.agent.server.ai.tool.PrepareTools;
import com.weiyi.agent.server.ai.tool.TicketQueryTools;
import com.weiyi.agent.server.ai.tool.ToolContexts;
import com.weiyi.agent.server.ai.tool.ToolExecutionContext;
import com.weiyi.agent.server.ai.tool.WorkMemory;
import com.weiyi.agent.server.ai.tool.WorkMemoryStore;
import com.weiyi.agent.server.ai.tool.WorkState;
import com.weiyi.agent.server.config.AgentProperties;
import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.server.dto.ChatVO;
import com.weiyi.agent.server.dto.PendingConfirmVO;
import com.weiyi.agent.server.entity.AConversation;
import com.weiyi.agent.server.entity.AMessage;
import com.weiyi.agent.server.entity.AModel;
import com.weiyi.agent.server.entity.AQuickCard;
import com.weiyi.agent.server.entity.AUserModel;
import com.weiyi.agent.server.req.AUsageLogReq;
import com.weiyi.agent.server.req.ChatReq;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.trace.TraceIdSupport;
import com.weiyi.agent.server.entity.ModelParams;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.common.core.util.JacksonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class ChatService {

    private final AUserModelService userModelService;
    private final AModelService modelService;
    private final AUsageLogService usageLogService;
    private final AConversationService conversationService;
    private final AQuickCardService quickCardService;
    private final AMessageService messageService;
    private final ConversationTitleService conversationTitleService;
    private final AgentProperties agentProperties;
    private final LlmProviderRegistry llmProviderRegistry;
    private final ThreadPoolTaskExecutor chatExecutor;
    private final PromptTemplates promptTemplates;
    private final PromptAssembler promptAssembler;
    private final ChatSecurityGuard chatSecurityGuard;
    private final MemoryCommandService memoryCommandService;
    private final MemoryService memoryService;
    private final ChatRoundPersistService chatRoundPersistService;
    private final IntentRouterService intentRouterService;
    private final WorkMemoryStore workMemoryStore;
    private final TicketQueryTools ticketQueryTools;
    private final PassengerTools passengerTools;
    private final PrepareTools prepareTools;
    private final OrderQueryTools orderQueryTools;
    private final AccountConversationService accountConversationService;
    private final ScheduledExecutorService titleProbeScheduler;

    public ChatService(AUserModelService userModelService,
                       AModelService modelService,
                       AUsageLogService usageLogService,
                       AConversationService conversationService,
                       AQuickCardService quickCardService,
                       AMessageService messageService,
                       ConversationTitleService conversationTitleService,
                       AgentProperties agentProperties,
                       LlmProviderRegistry llmProviderRegistry,
                       ThreadPoolTaskExecutor chatExecutor,
                       PromptTemplates promptTemplates,
                       PromptAssembler promptAssembler,
                       ChatSecurityGuard chatSecurityGuard,
                       MemoryCommandService memoryCommandService,
                       MemoryService memoryService,
                       ChatRoundPersistService chatRoundPersistService,
                       IntentRouterService intentRouterService,
                       WorkMemoryStore workMemoryStore,
                       TicketQueryTools ticketQueryTools,
                       PassengerTools passengerTools,
                       PrepareTools prepareTools,
                       OrderQueryTools orderQueryTools,
                       AccountConversationService accountConversationService,
                       ScheduledExecutorService titleProbeScheduler) {
        this.userModelService = userModelService;
        this.modelService = modelService;
        this.usageLogService = usageLogService;
        this.conversationService = conversationService;
        this.quickCardService = quickCardService;
        this.messageService = messageService;
        this.conversationTitleService = conversationTitleService;
        this.agentProperties = agentProperties;
        this.llmProviderRegistry = llmProviderRegistry;
        this.chatExecutor = chatExecutor;
        this.promptTemplates = promptTemplates;
        this.promptAssembler = promptAssembler;
        this.chatSecurityGuard = chatSecurityGuard;
        this.memoryCommandService = memoryCommandService;
        this.memoryService = memoryService;
        this.chatRoundPersistService = chatRoundPersistService;
        this.intentRouterService = intentRouterService;
        this.workMemoryStore = workMemoryStore;
        this.ticketQueryTools = ticketQueryTools;
        this.passengerTools = passengerTools;
        this.prepareTools = prepareTools;
        this.orderQueryTools = orderQueryTools;
        this.accountConversationService = accountConversationService;
        this.titleProbeScheduler = titleProbeScheduler;
    }

    /**
     * 交谈：请求线程只做校验、会话定位（账户指针/幂等回执/忙拒绝/翻篇）与首事件下发；
     * 回复经 SSE 逐段推送，流结束/失败在 chatExecutor 异步落用量与整轮消息、
     * 收尾请求回执并释放主回复占用（一条 AMessage=一轮，本轮结束才成行；
     * 失败轮 reply 留空、用户输入兜底保留——落库对用户可延迟，但必须有记录）。
     * 身份由调用方（Controller 的 LoginContext）传入，异步回调不继承请求 ThreadLocal。
     */
    public SseEmitter stream(Long userId, ChatReq chatReq) {
        AModel currentModel = modelService.getCurrentModel();

        // 幂等锚点：必填且长度受限，任何重试必须换新值
        String clientRequestId = chatReq.getClientRequestId();
        BizAssert.isTrue(clientRequestId != null && !clientRequestId.isBlank()
                && clientRequestId.length() <= 64, ErrorCode.PARAM_ERROR, "clientRequestId 不能为空且不超过 64 字符");

        // 获取本次用户消息内容（lambda 回调捕获，需定值赋值）
        final String context;
        final Long cardId;
        // 卡片轮渲染进 chat 模板的固定 card_data 数据段；文本轮为“无”
        final String cardData;
        // 标题素材：卡片轮取用户可见卡片文，文本轮取原文
        final String titleSource;
        if (chatReq.getCardId() != null) {
            AQuickCard quickCard = quickCardService.getById(chatReq.getCardId());
            BizAssert.isTrue(quickCard.getStatus() == AgentConstants.STATUS_ON, ErrorCode.QUICK_CARD_NOT_ENABLE);
            // 卡片提示词来自服务端库不重复做高危判定；用户补充内容（userContext 或 content）仍按自由文本校验
            context = quickCard.getPromptText();
            cardId = quickCard.getId();
            titleSource = quickCard.getCardName();
            String supplementText = chatReq.getUserContext() != null ? chatReq.getUserContext() : chatReq.getContent();
            String supplement = supplementText == null
                    ? "无" : chatSecurityGuard.checkText(supplementText);
            cardData = "cardName: " + quickCard.getCardName()
                    + "\ncardPrompt: " + quickCard.getPromptText()
                    + "\nuserContext: " + supplement;
        } else if (chatReq.getContent() != null && !chatReq.getContent().isBlank()) {
            context = chatSecurityGuard.checkText(chatReq.getContent());
            cardId = null;
            cardData = "无";
            titleSource = context;
        } else {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "本次对话无消息内容");
        }
        // 对用户消息内容进行消毒
        final String maskedContext = cardId == null ? PromptSanitizer.sanitize(context) : null;

        // 定位会话（指针行锁内串行）：回执幂等、忙拒绝、72h 翻篇、首建或复用；
        // 命中成功回执返回重放轮，不重复建会话、不重复执行任务
        AccountConversationService.Located located = accountConversationService
                .locateForSend(userId, clientRequestId, currentModel.getId());
        Long conversationId = located.getConversationId();

        // 首轮即定标题（请求线程）：短输入清洗后直落（不经 MQ，meta 直接带正式标题、无需流内探测）；
        // 长输入投递事件携带首条输入快照，消费不依赖本轮消息落库；
        // 素材口径与喂模型一致：文本轮用净化版，卡片轮取卡片名（服务端文本）
        boolean titleSettled = located.isFirstRound()
                && conversationTitleService.onFirstRound(conversationId,
                        cardId == null ? maskedContext : titleSource);
        AConversation conversation = conversationService.getById(conversationId);

        // 超时后容器关流，推送转静默继续攒全量，流结束照常落库
        SseEmitter emitter = new SseEmitter(agentProperties.getChat().getSseTimeoutMillis());
        sendQuietly(emitter, SseEmitter.event().name("meta").data(
                metaVO(conversation, currentModel.getId(), located.isRotated()), MediaType.APPLICATION_JSON));

        if (located.getReplay() != null) {
            return replayRound(emitter, conversation, located.getReplay());
        }

        // 授权/配额校验（会话定位之后）：业务性失败不抛异常，走 SSE error + 错误文案落库——
        // 用户侧以消息形式收到提示（如"暂不支持使用 AI 交流功能"），历史回看与当场提示一致
        try {
            userModelVerifier(userModelService.ensureByUserId(userId));
        } catch (BusinessException e) {
            return errorRound(emitter, conversationId, userId, currentModel.getId(),
                    cardId, context, maskedContext, e.getMessage(), clientRequestId);
        }

        // 首轮标题流内探测（直落已定稿则不开）：低频查库，标题先于主回复完成时即时下发一次 title 事件
        ScheduledFuture<?> titleProbe = located.isFirstRound() && !titleSettled
                ? startTitleProbe(emitter, conversationId) : null;

        try {
            // 显式记忆指令（记住/忘掉/查看记忆等）：同步执行并以固定文案回复，不调主模型
            java.util.Optional<String> commandReply = memoryCommandService.tryExecute(userId, context);
            if (commandReply.isPresent()) {
                cancelProbe(titleProbe);
                return commandRound(emitter, conversationId, userId, currentModel.getId(),
                        cardId, context, maskedContext, OutputSafety.scrub(commandReply.get()), clientRequestId);
            }

            // FSM 路由编排：IDLE 顶层路由；非 IDLE 状态内切换判定（SWITCH 清任务实体后重新路由）
            // 返回有效分支的 FSM 状态（clarify 轮保持 IDLE），供工具门禁与场景模板共同消费
            String branchFsm = resolveBranch(userId, conversationId, context);
            PromptScene branchScene = sceneOf(branchFsm);

            // 分支上下文（工具由分支白名单随请求注册，身份经 toolContext 服务端注入，不进模型 schema；
            // 工具按 FSM 状态串门禁，与 ToolExecutionContext.state 对齐）
            List<Message> messages = buildBranchContext(branchScene, conversationId, userId, context, cardData);
            ChatClient.ChatClientRequestSpec promptSpec = llmProviderRegistry
                    .getChatClientWithoutSafeguard(currentModel.getId())
                    .prompt()
                    .toolContext(Map.of(ToolContexts.KEY, new ToolExecutionContext(
                            userId, conversationId, TraceIdSupport.current(), branchFsm, null)));
            registerBranchTools(promptSpec, branchScene);

            String traceId = TraceIdSupport.current();
            // 思考块剥离（模型级开关）：部分思考模型拒绝关闭参数且思考混在正文，剥离后才进脱敏与落库口径
            boolean stripThinking = stripThinkingOf(currentModel);
            ThinkTagStripper stripper = new ThinkTagStripper();
            // 流式脱敏滑窗：尾部候选不闭合不出门，同时累积已下发的安全文本作为落库口径
            StreamScrubber scrubber = new StreamScrubber();
            // usage 缺失兜底：多数上游仅末片携带，异常/不带时保持 null，落库三段留空
            AtomicReference<Usage> lastUsage = new AtomicReference<>();
            AtomicBoolean clientGone = new AtomicBoolean(false);
            long start = System.currentTimeMillis();

            promptSpec.messages(messages).stream().chatResponse()
                    // 用 chatResponse() 而非 content()：chunk 兼带 usage，仅末片才有
                    .doOnNext(chunk -> {
                        if (chunk.getMetadata() != null && chunk.getMetadata().getUsage() != null) {
                            lastUsage.set(chunk.getMetadata().getUsage());
                        }
                        String delta = ChatTexts.delta(chunk);
                        if (delta == null) {
                            return;
                        }
                        String body = stripThinking ? stripper.feed(delta) : delta;
                        String safe = body.isEmpty() ? "" : scrubber.feed(body);
                        if (!clientGone.get() && !safe.isEmpty()) {
                            try {
                                emitter.send(safe);
                            } catch (Exception e) {
                                clientGone.set(true);
                            }
                        }
                    })
                    // doOnNext 内禁止阻塞：落库全部移到结束/失败回调，交 chatExecutor
                    .subscribe(
                            chunk -> { }, // onNext：每个 chunk 到来时调用
                            err -> chatExecutor.execute(() -> withTrace(traceId, () -> {
                                cancelProbe(titleProbe);
                                try {
                                    failRound(conversationId, userId, currentModel.getId(),
                                            cardId, context, maskedContext, start, emitter, clientGone, scrubber, err, clientRequestId);
                                } catch (Exception e) {
                                    abortRound(conversationId, userId, clientRequestId, emitter, clientGone, e);
                                }
                            })),
                            () -> chatExecutor.execute(() -> withTrace(traceId, () -> {
                                cancelProbe(titleProbe);
                                try {
                                    finishRound(conversationId, userId, currentModel.getId(),
                                            cardId, context, maskedContext, start, TokenUsage.of(lastUsage.get()),
                                            emitter, clientGone, scrubber, stripper, stripThinking, clientRequestId);
                                } catch (Exception e) {
                                    abortRound(conversationId, userId, clientRequestId, emitter, clientGone, e);
                                }
                            })));
            return emitter;
        } catch (Exception e) {
            // 推流前失败（路由/上下文组装异常）：回执置 FAILED 释放占用，异常交全局处理器返回 Result
            cancelProbe(titleProbe);
            accountConversationService.completeRequest(userId, clientRequestId, null, e.getMessage());
            throw e;
        }
    }

    /**
     * 业务性失败轮（授权关闭/超配额等）：固定文案即本轮回复落库（不调模型、无用量、不入队提炼），
     * SSE 发 error 事件携带同一文案——前端以 AI 消息气泡告知用户，历史回看与当场提示一致。
     */
    private SseEmitter errorRound(SseEmitter emitter, Long conversationId, Long userId, Long modelId,
                                  Long cardId, String context, String maskedContext,
                                  String userFacingMsg, String clientRequestId) {
        Long messageId = chatRoundPersistService.saveRoundOnly(conversationId, userId, cardId,
                context, maskedContext, userFacingMsg, modelId, null);
        accountConversationService.completeRequest(userId, clientRequestId, messageId, null);
        sendQuietly(emitter, SseEmitter.event().name("error").data(userFacingMsg));
        completeQuietly(emitter);
        return emitter;
    }

    /** 幂等重放：原请求已成功，meta 后直接回放已落库整轮，不重复执行任何任务 */
    private SseEmitter replayRound(SseEmitter emitter, AConversation conversation, AMessage replay) {
        if (replay.getReply() != null && !replay.getReply().isEmpty()) {
            sendQuietly(emitter, replay.getReply());
        }
        sendQuietly(emitter, SseEmitter.event().name("done").data(
                doneVO(conversation.getId(), replay.getId()), MediaType.APPLICATION_JSON));
        completeQuietly(emitter);
        return emitter;
    }

    /**
     * FSM 分支解析：IDLE 走顶层意图路由；非 IDLE 走状态内切换判定——
     * CONTINUE 留当前分支、SWITCH 清任务实体后重新路由、SIDE 仅查票有效（服务端已归一为 CONTINUE）。
     * 路由/切换结构化失败分别降级 chat / CONTINUE，不阻断主回复。
     * 返回有效分支的 FSM 状态（工具门禁按此比对）；clarify 意图保持 IDLE，下轮重新路由。
     */
    private String resolveBranch(Long userId, Long conversationId, String currentInput) {
        WorkState workState = workMemoryStore.getState(conversationId);
        if (AgentConstants.FSM_IDLE.equals(workState.getState())) {
            String intent = intentRouterService.route(userId, conversationId, currentInput);
            return advanceState(conversationId, workState, intent);
        }
        String decision = intentRouterService.switchDecision(userId, conversationId, workState.getState(), currentInput);
        if (AgentConstants.SWITCH_SWITCH.equals(decision)) {
            // 换任务：清业务实体（查询快照/待确认镜像），仅保留账户长期记忆
            workMemoryStore.saveMemory(conversationId, new WorkMemory());
            workState.setAgentSub(null);
            String intent = intentRouterService.route(userId, conversationId, currentInput);
            return advanceState(conversationId, workState, intent);
        }
        return workState.getState();
    }

    /** 路由意图落 FSM 状态并返回（clarify 保持 IDLE 不改状态） */
    private String advanceState(Long conversationId, WorkState workState, String intent) {
        if (AgentConstants.INTENT_CLARIFY.equals(intent)) {
            return AgentConstants.FSM_IDLE;
        }
        String fsm = intentRouterService.intentToFsm(intent);
        workState.setState(fsm);
        workState.setFailCount(0);
        workMemoryStore.saveState(conversationId, workState);
        return fsm;
    }

    /** FSM 状态 → 分支模板场景（IDLE 作为分支仅出现在 clarify 轮）
     * FSM_QUERY -> QUERY(query-system.st / query-user.st)
     */
    private PromptScene sceneOf(String fsm) {
        return switch (fsm) {
            case AgentConstants.FSM_QUERY -> PromptScene.QUERY;
            case AgentConstants.FSM_BUY -> PromptScene.BUY;
            case AgentConstants.FSM_REFUND -> PromptScene.REFUND;
            case AgentConstants.FSM_IDLE -> PromptScene.CLARIFY;
            default -> PromptScene.CHAT;
        };
    }

    /** 分支工具白名单：CHAT/CLARIFY 无业务工具；查询=站点/车票/详情；购票=查询+乘车人+预检；退票=订单+预检。execute 永不注册 */
    private void registerBranchTools(ChatClient.ChatClientRequestSpec spec, PromptScene scene) {
        switch (scene) {
            case QUERY -> spec.tools(ticketQueryTools);
            case BUY -> spec.tools(ticketQueryTools, passengerTools, prepareTools);
            case REFUND -> spec.tools(orderQueryTools, prepareTools);
            default -> {
                // CHAT / CLARIFY：不注册业务工具
            }
        }
    }

    /**
     * 分支上下文组装：system 经装配器追加安全常量；各分支按模板槽位渲染——
     * 长期记忆/会话摘要（仅 chat）与卡片数据段以随机边界包裹，历史与本轮输入渲染前净化；
     * 业务分支注入服务端当前日期与任务状态摘要，"明天/后天"由模型按此换算。
     */
    private List<Message> buildBranchContext(PromptScene scene, Long conversationId, Long userId,
                                             String currentInput, String cardData) {
        boolean cardRound = !"无".equals(cardData);
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(promptAssembler.secureSystem(
                promptTemplates.system(scene).render(), true)));
        String userPrompt = switch (scene) {
            case CLARIFY -> promptTemplates.user(scene).render(Map.of(
                    "historyMessages", sanitizedHistory(conversationId),
                    "userContent", PromptSanitizer.sanitize(currentInput)));
            case QUERY, BUY, REFUND -> promptTemplates.user(scene).render(Map.of(
                    "currentDate", LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                    "taskState", promptAssembler.wrap("tool", branchTaskSummary(conversationId)),
                    "historyMessages", sanitizedHistory(conversationId),
                    "userContent", PromptSanitizer.sanitize(currentInput)));
            default -> promptTemplates.user(scene).render(Map.of(
                    "longTermMemory", promptAssembler.wrap("memory",
                            memoryService.enabledMemoriesForPrompt(userId)),
                    "conversationSummary", promptAssembler.wrap("summary",
                            chatRoundPersistService.summaryOf(conversationId)),
                    "historyMessages", sanitizedHistory(conversationId),
                    "cardData", cardRound ? promptAssembler.wrap("card", cardData) : "无",
                    "userContent", cardRound ? "无" : PromptSanitizer.sanitize(currentInput)));
        };
        messages.add(new UserMessage(userPrompt));
        return messages;
    }

    private String sanitizedHistory(Long conversationId) {
        return PromptSanitizer.sanitize(
                messageService.latestAsText(conversationId, agentProperties.getMessageCount()));
    }

    /** 业务分支任务状态摘要：最近查询条件与车票快照（工作记忆实体，随读写滑动续期） */
    private String branchTaskSummary(Long conversationId) {
        WorkMemory memory = workMemoryStore.getMemory(conversationId);
        WorkMemory.LastQuery lastQuery = memory.getLastQuery();
        if (lastQuery == null && (memory.getLastTickets() == null || memory.getLastTickets().getItems().isEmpty())) {
            return "无";
        }
        StringBuilder sb = new StringBuilder();
        if (lastQuery != null) {
            sb.append("最近查询：").append(lastQuery.getFromName()).append("→").append(lastQuery.getToName())
                    .append(" ").append(lastQuery.getRideDate());
        }
        if (memory.getLastTickets() != null && !memory.getLastTickets().getItems().isEmpty()) {
            if (sb.length() > 0) {
                sb.append("；");
            }
            sb.append("车票列表 ").append(memory.getLastTickets().getItems().size()).append(" 条");
        }
        return sb.toString();
    }

    /**
     * 显式记忆指令轮：同步落库（无模型调用、无用量流水）+ 单段推送 + done + 回执收尾。
     * 标题已在首轮（会话创建后）处理，此处不再投递；提炼任务由落库事务按开关决定（固定回复也入队，素材完整）。
     */
    private SseEmitter commandRound(SseEmitter emitter, Long conversationId, Long userId, Long modelId,
                                    Long cardId, String context, String maskedContext, String reply,
                                    String clientRequestId) {
        Long messageId = chatRoundPersistService.saveRoundWithTasks(
                conversationId, userId, cardId, context, maskedContext, reply, modelId, null);
        accountConversationService.completeRequest(userId, clientRequestId, messageId, null);
        sendQuietly(emitter, reply);
        sendQuietly(emitter, SseEmitter.event().name("done").data(
                doneVO(conversationId, messageId), MediaType.APPLICATION_JSON));
        completeQuietly(emitter);
        return emitter;
    }

    /** 流结束回调：先落用量（token 异常三段留空）→ 事务落整轮消息与记忆任务 →
     *  回执置 SUCCESS 释放占用 → flush 尾部 → done 事件（本轮 id 此时才成行，title 未就绪由前端轮询兜底） */
    private void finishRound(Long conversationId, Long userId, Long modelId, Long cardId, String context,
                             String maskedContext, long start, TokenUsage tokens, SseEmitter emitter,
                             AtomicBoolean clientGone, StreamScrubber scrubber,
                             ThinkTagStripper stripper, boolean stripThinking, String clientRequestId) {
        // 落库口径 = 滑窗累积的已下发布安全文本（flush 尾部并入累积）；先 flush 再取，库与前端同源
        flushQuietly(emitter, scrubber, stripper, stripThinking);
        Long usageLogId = recordUsage(userId, modelId, conversationId,
                System.currentTimeMillis() - start, AgentConstants.CALL_STATUS_SUCCESS, null, tokens);
        Long messageId = chatRoundPersistService.saveRoundWithTasks(conversationId, userId, cardId,
                context, maskedContext, scrubber.emittedText(), modelId, usageLogId);
        // 成功轮清连续失败计数并滑动续期工作记忆
        WorkState workState = workMemoryStore.getState(conversationId);
        workState.setFailCount(0);
        workMemoryStore.saveState(conversationId, workState);
        accountConversationService.completeRequest(userId, clientRequestId, messageId, null);
        if (clientGone.get()) {
            return;
        }
        sendConfirmationQuietly(emitter, conversationId);
        sendQuietly(emitter, SseEmitter.event().name("done").data(
                doneVO(conversationId, messageId), MediaType.APPLICATION_JSON));
        completeQuietly(emitter);
    }

    /**
     * 预检轮收尾推送确认卡片：工作记忆镜像存在待确认单时下发 confirmation 事件，
     * 载荷只含展示字段（type/summary/expiresAt），操作 id 不出服务端；
     * 前端弹底部确认表单，确认动作走 /ai/order 意图接口。
     */
    private void sendConfirmationQuietly(SseEmitter emitter, Long conversationId) {
        WorkMemory.PendingConfirm pending = workMemoryStore.getMemory(conversationId).getPendingConfirm();
        if (pending == null) {
            return;
        }
        PendingConfirmVO vo = new PendingConfirmVO();
        vo.setType(pending.getType());
        vo.setSummary(pending.getSummary());
        vo.setExpiresAt(pending.getExpiresAt());
        sendQuietly(emitter, SseEmitter.event().name("confirmation").data(vo, MediaType.APPLICATION_JSON));
    }

    /**
     * 流失败回调：落失败用量 + 错误轮（用户向文案覆盖式落库，已流出部分不落、前端同款覆盖展示）
     * + 回执置 SUCCESS 关联消息（同 id 重试重放错误轮，不重复烧模型），原始异常只留日志。
     */
    private void failRound(Long conversationId, Long userId, Long modelId, Long cardId, String context,
                           String maskedContext, long start, SseEmitter emitter, AtomicBoolean clientGone,
                           StreamScrubber scrubber, Throwable err, String clientRequestId) {
        String userFacingMsg = ErrorClassifier.userFacing(err);
        Long usageLogId = recordUsage(userId, modelId, conversationId, System.currentTimeMillis() - start,
                AgentConstants.CALL_STATUS_FAIL, err.getMessage(), null);
        Long messageId = chatRoundPersistService.saveRoundOnly(conversationId, userId, cardId,
                context, maskedContext, userFacingMsg, modelId, usageLogId);
        // 连续失败计数：达阈值强制回 IDLE 并清待确认状态（工作记忆 TTL 到期有同样效果，这里提前兜底）
        WorkState workState = workMemoryStore.getState(conversationId);
        int failCount = (workState.getFailCount() == null ? 0 : workState.getFailCount()) + 1;
        if (failCount >= AgentConstants.FSM_FAIL_LIMIT) {
            workState.setState(AgentConstants.FSM_IDLE);
            workState.setAgentSub(null);
            workState.setFailCount(0);
            workMemoryStore.setPendingConfirm(conversationId, null);
        } else {
            workState.setFailCount(failCount);
        }
        workMemoryStore.saveState(conversationId, workState);
        log.warn("AI 流式回复失败：conversationId={}, userFacing={}", conversationId, userFacingMsg, err);
        accountConversationService.completeRequest(userId, clientRequestId, messageId, null);
        if (clientGone.get()) {
            return;
        }
        sendQuietly(emitter, SseEmitter.event().name("error").data(userFacingMsg));
        completeQuietly(emitter);
    }

    /**
     * 收尾兜底：异步收尾（失败轮或成功轮）任一步抛出时，仍必须释放主回复占用并关流——
     * 占用悬挂会让用户一直停在“思考中”且无法再发送，只能等孤儿占用过期回收或重启服务。
     */
    private void abortRound(Long conversationId, Long userId, String clientRequestId,
                            SseEmitter emitter, AtomicBoolean clientGone, Exception cause) {
        log.error("本轮收尾失败，回执置失败并释放占用：conversationId={}, clientRequestId={}",
                conversationId, clientRequestId, cause);
        try {
            accountConversationService.completeRequest(userId, clientRequestId, null,
                    cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage());
        } catch (Exception e) {
            log.error("释放主回复占用失败：userId={}, clientRequestId={}", userId, clientRequestId, e);
        }
        if (!clientGone.get()) {
            sendQuietly(emitter, SseEmitter.event().name("error").data(ErrorClassifier.userFacing(cause)));
            completeQuietly(emitter);
        }
    }

    /** 流结束收尾：先收思考剥离器（判定期放行/闭合后正文），产物再过脱敏滑窗，最后滑窗 flush；
     *  两级产出均进推送与 emittedText 落库口径 */
    private void flushQuietly(SseEmitter emitter, StreamScrubber scrubber,
                              ThinkTagStripper stripper, boolean stripThinking) {
        if (stripThinking) {
            String thinkTail = stripper.flush();
            if (!thinkTail.isEmpty()) {
                String safe = scrubber.feed(thinkTail);
                if (!safe.isEmpty()) {
                    sendQuietly(emitter, safe);
                }
            }
        }
        String tail = scrubber.flush();
        if (!tail.isEmpty()) {
            sendQuietly(emitter, tail);
        }
    }

    /** 模型参数快照是否开启思考剥离（params 非法/为空视为关闭） */
    private boolean stripThinkingOf(AModel model) {
        ModelParams params = JacksonUtil.fromJson(model.getParams(), ModelParams.class);
        return params != null && Boolean.TRUE.equals(params.getStripThinking());
    }

    /** 首事件载荷：会话/模型 id 与标题状态；翻篇时携带 conversationReset 供前端插“新会话”分隔行 */
    private ChatVO metaVO(AConversation conversation, Long modelId, boolean conversationReset) {
        ChatVO vo = new ChatVO();
        vo.setConversationId(conversation.getId());
        vo.setRole(AgentConstants.MESSAGE_ROLE_ASSISTANT);
        vo.setChatModelId(modelId);
        vo.setTitle(conversation.getTitle());
        vo.setTitleStatus(AgentConstants.titleStatusName(conversation.getTitleStatus()));
        vo.setConversationReset(conversationReset);
        return vo;
    }

    /** 末事件载荷：整轮流结束才成行，重读会话取最终标题/状态（未就绪由前端轮询兜底） */
    private ChatVO doneVO(Long conversationId, Long messageId) {
        AConversation conversation = conversationService.getById(conversationId);
        ChatVO vo = new ChatVO();
        vo.setConversationId(conversationId);
        vo.setMessageId(messageId);
        vo.setTitle(conversation.getTitle());
        vo.setTitleStatus(AgentConstants.titleStatusName(conversation.getTitleStatus()));
        return vo;
    }

    /**
     * 首轮标题流内探测：低频查库，标题先于主回复完成时即时下发一次 title 事件并自停；
     * 超时停测由 done 事件兜底（done 携带当时状态，仍不就绪则前端轮询 current）。
     */
    private ScheduledFuture<?> startTitleProbe(SseEmitter emitter, Long conversationId) {
        long interval = agentProperties.getConversation().getTitleProbeIntervalMillis();
        long deadline = System.currentTimeMillis() + agentProperties.getConversation().getTitleProbeMaxMillis();
        AtomicReference<ScheduledFuture<?>> self = new AtomicReference<>();
        ScheduledFuture<?> future = titleProbeScheduler.scheduleAtFixedRate(() -> {
            ScheduledFuture<?> own = self.get();
            if (own != null && System.currentTimeMillis() > deadline) {
                own.cancel(false);
                return;
            }
            try {
                AConversation conversation = conversationService.getById(conversationId);
                if (!AgentConstants.CONVERSATION_TITLE_PLACEHOLDER.equals(conversation.getTitle())) {
                    ChatVO vo = new ChatVO();
                    vo.setConversationId(conversationId);
                    vo.setTitle(conversation.getTitle());
                    vo.setTitleStatus(AgentConstants.titleStatusName(conversation.getTitleStatus()));
                    sendQuietly(emitter, SseEmitter.event().name("title").data(vo, MediaType.APPLICATION_JSON));
                    if (own != null) {
                        own.cancel(false);
                    }
                }
            } catch (Exception e) {
                log.warn("标题流内探测失败（忽略）：conversationId={}: {}", conversationId, e.getMessage());
            }
        }, interval, interval, TimeUnit.MILLISECONDS);
        self.set(future);
        return future;
    }

    private void cancelProbe(ScheduledFuture<?> probe) {
        if (probe != null) {
            probe.cancel(false);
        }
    }

    /** 异步落库脱离请求线程：手动恢复/清理 MDC，防异步日志脱轨与池化线程脏串 */
    private void withTrace(String traceId, Runnable task) {
        TraceIdSupport.bind(traceId);
        try {
            task.run();
        } finally {
            TraceIdSupport.clear();
        }
    }

    /** 推送失败不外抛：断开由 doOnNext 的 send 探测兜住，这里只保证回调自身不炸 */
    private void sendQuietly(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (Exception e) {
            log.warn("SSE 事件推送失败（客户端可能已断开）：{}", e.getMessage());
        }
    }

    /** 纯文本推送重载（滑窗 flush 出的尾部增量） */
    private void sendQuietly(SseEmitter emitter, String data) {
        try {
            emitter.send(data);
        } catch (Exception e) {
            log.warn("SSE 文本推送失败（客户端可能已断开）：{}", e.getMessage());
        }
    }

    /** 超时/断开后二次 complete 不外抛 */
    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception e) {
            log.warn("SSE 关流失败（可能已超时关闭）：{}", e.getMessage());
        }
    }

    /** 落用量流水，返回流水 id；tokens 为 null（失败调用上游无用量）时三段留空。
     *  流水是附属动作：异常只告警不抛——收尾抛出会让主回复占用永不释放，用户卡在“思考中”无法再发送。 */
    private Long recordUsage(Long userId, Long modelId, Long conversationId, long latencyMs,
                             int callStatus, String errMsg, TokenUsage tokens) {
        try {
            AUsageLogReq req = new AUsageLogReq();
            req.setUserId(userId);
            req.setModelId(modelId);
            req.setConversationId(conversationId);
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
            log.warn("用量流水记录失败（已忽略）：conversationId={}, callStatus={}, tokens={}: {}",
                    conversationId, callStatus, tokens, e.getMessage());
            return null;
        }
    }

    /**
     * 校验 userModel 的可行性
     */
    private void userModelVerifier(AUserModel userModel) {
        if (userModel.getStatus() == AgentConstants.STATUS_OFF) {
            throw new BusinessException(ErrorCode.NO_SUPPORT_AI_CHAT);
        }
        if (userModel.getDailyQuota() != 0 && userModel.getStatus() == AgentConstants.STATUS_ON) {
            Integer count = usageLogService.countWithSuccessAndToday(userModel.getUserId());
            BizAssert.isTrue(count < userModel.getDailyQuota(), ErrorCode.MODEL_USAGE_LIMIT);
        }
    }
}
