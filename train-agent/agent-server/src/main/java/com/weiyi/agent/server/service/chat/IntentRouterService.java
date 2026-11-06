package com.weiyi.agent.server.service.chat;
import com.weiyi.agent.server.service.AUsageLogService;
import com.weiyi.agent.server.service.conversation.AMessageService;
import com.weiyi.agent.server.service.model.AModelService;

import com.weiyi.agent.server.ai.provider.LlmProviderRegistry;
import com.weiyi.agent.server.ai.prompt.PromptAssembler;
import com.weiyi.agent.server.constant.PromptScene;
import com.weiyi.agent.server.ai.prompt.PromptTemplates;
import com.weiyi.agent.server.ai.prompt.StructuredOutputInvoker;
import com.weiyi.agent.server.config.AgentProperties;
import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.server.dto.IntentDecision;
import com.weiyi.agent.server.dto.TaskSwitchDecision;
import com.weiyi.agent.server.entity.AModel;
import com.weiyi.agent.server.ai.tool.WorkMemory;
import com.weiyi.agent.server.ai.tool.WorkMemoryStore;
import com.weiyi.agent.server.ai.tool.WorkState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * 意图路由与任务切换判定：IDLE 顶层路由 + 非 IDLE 状态内切换。
 * 路由调用 temperature 低、非流式、工具集为空；结构化失败降级 chat / CONTINUE，不阻断主回复。
 * RAG 未开放：rag_question 由服务端降级 chat；SIDE 仅查票任务有效，其余按 CONTINUE。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntentRouterService {

    /** 合法意图枚举 */
    private static final Set<String> INTENTS = Set.of(
            AgentConstants.INTENT_CHAT, AgentConstants.INTENT_QUERY, AgentConstants.INTENT_BUY,
            AgentConstants.INTENT_REFUND, AgentConstants.INTENT_RAG, AgentConstants.INTENT_CLARIFY);

    private final AModelService modelService;
    private final LlmProviderRegistry llmProviderRegistry;
    private final PromptTemplates promptTemplates;
    private final PromptAssembler promptAssembler;
    private final StructuredOutputInvoker structuredOutputInvoker;
    private final AMessageService messageService;
    private final WorkMemoryStore workMemoryStore;
    private final AUsageLogService usageLogService;
    private final AgentProperties agentProperties;

    /**
     * IDLE 顶层路由：返回归一化意图（非法枚举/低置信/结构化失败 → chat 或 clarify；rag → chat）。
     */
    public String route(Long userId, Long conversationId, String currentInput) {
        AModel model = modelService.getCurrentModel();
        long start = System.currentTimeMillis();
        try {
            String intent = doRoute(model, userId, conversationId, currentInput);
            recordRouterUsage(userId, model.getId(), conversationId,
                    System.currentTimeMillis() - start, AgentConstants.CALL_STATUS_SUCCESS, null);
            return intent;
        } catch (Exception e) {
            recordRouterUsage(userId, model.getId(), conversationId,
                    System.currentTimeMillis() - start, AgentConstants.CALL_STATUS_FAIL, e.getMessage());
            throw e;
        }
    }

    private String doRoute(AModel model, Long userId, Long conversationId, String currentInput) {
        BeanOutputConverter<IntentDecision> converter = new BeanOutputConverter<>(IntentDecision.class);
        String systemPrompt = promptAssembler.secureSystem(
                promptTemplates.system(PromptScene.INTENT_ROUTER).render(), false);
        String userPrompt = promptTemplates.user(PromptScene.INTENT_ROUTER).render(Map.of(
                "sessionState", sessionStateText(conversationId),
                "historyMessages", messageService.latestAsText(conversationId,
                        agentProperties.getRouter().getRouterMessageCount()),
                "userContent", currentInput));
        // 路由喂近期消息文本，历史可能含敏感词：用无 SafeGuard 客户端（入口守卫是输入防线）
        IntentDecision decision = structuredOutputInvoker.invoke(
                llmProviderRegistry.getChatClientWithoutSafeguard(model.getId()),
                systemPrompt, userPrompt, converter, new IntentDecision(AgentConstants.INTENT_CHAT, 0.0, "FALLBACK"),
                "intent-router");
        String intent = normalizeIntent(decision);
        log.info("[router] userId={}, conversationId={}, intent={}, confidence={}, reason={}",
                userId, conversationId, intent, decision.confidence(), decision.reasonCode());
        return intent;
    }

    /**
     * 非 IDLE 状态内切换判定：CONTINUE / SWITCH / SIDE（SIDE 仅查票有效，其余安全默认 CONTINUE）；
     * 失败默认 CONTINUE。返回归一化决策。
     */
    public String switchDecision(Long userId, Long conversationId, String currentState, String currentInput) {
        AModel model = modelService.getCurrentModel();
        long start = System.currentTimeMillis();
        try {
            String decision = doSwitchDecision(model, conversationId, currentState, currentInput);
            recordRouterUsage(userId, model.getId(), conversationId,
                    System.currentTimeMillis() - start, AgentConstants.CALL_STATUS_SUCCESS, null);
            return decision;
        } catch (Exception e) {
            recordRouterUsage(userId, model.getId(), conversationId,
                    System.currentTimeMillis() - start, AgentConstants.CALL_STATUS_FAIL, e.getMessage());
            throw e;
        }
    }

    private String doSwitchDecision(AModel model, Long conversationId, String currentState, String currentInput) {
        BeanOutputConverter<TaskSwitchDecision> converter = new BeanOutputConverter<>(TaskSwitchDecision.class);
        String systemPrompt = promptAssembler.secureSystem(
                promptTemplates.system(PromptScene.TASK_SWITCH).render(), false);
        String userPrompt = promptTemplates.user(PromptScene.TASK_SWITCH).render(Map.of(
                "currentState", currentState,
                "taskSummary", taskSummaryText(conversationId),
                "historyMessages", messageService.latestAsText(conversationId,
                        agentProperties.getRouter().getRouterMessageCount()),
                "userContent", currentInput));
        TaskSwitchDecision decision = structuredOutputInvoker.invoke(
                llmProviderRegistry.getChatClientWithoutSafeguard(model.getId()),
                systemPrompt, userPrompt, converter,
                new TaskSwitchDecision(AgentConstants.SWITCH_CONTINUE), "task-switch");
        String result = decision.decision() == null ? AgentConstants.SWITCH_CONTINUE : decision.decision().toUpperCase();
        if (!AgentConstants.SWITCH_CONTINUE.equals(result) && !AgentConstants.SWITCH_SWITCH.equals(result)
                && !AgentConstants.SWITCH_SIDE.equals(result)) {
            result = AgentConstants.SWITCH_CONTINUE;
        }
        if (AgentConstants.SWITCH_SIDE.equals(result) && !AgentConstants.FSM_QUERY.equals(currentState)) {
            // SIDE 只对查票补槽有效，其他状态收到由服务端改安全默认
            result = AgentConstants.SWITCH_CONTINUE;
        }
        log.info("[router] conversationId={}, state={}, decision={}", conversationId, currentState, result);
        return result;
    }

    /** 路由/切换调用的附属用量流水（结构化调用不透传 token，仅记次数与耗时；失败只告警不阻断） */
    private void recordRouterUsage(Long userId, Long modelId, Long conversationId,
                                   long latencyMs, int callStatus, String errMsg) {
        try {
            com.weiyi.agent.server.req.AUsageLogReq req = new com.weiyi.agent.server.req.AUsageLogReq();
            req.setUserId(userId);
            req.setModelId(modelId);
            req.setConversationId(conversationId);
            req.setLatencyMs((int) latencyMs);
            req.setCallStatus(callStatus);
            req.setErrMsg(errMsg);
            usageLogService.record(req);
        } catch (Exception e) {
            log.warn("[router] 用量记录失败（已忽略）：{}", e.getMessage());
        }
    }

    /** 意图 → FSM 分支状态映射 */
    public String intentToFsm(String intent) {
        return switch (intent) {
            case AgentConstants.INTENT_QUERY -> AgentConstants.FSM_QUERY;
            case AgentConstants.INTENT_BUY -> AgentConstants.FSM_BUY;
            case AgentConstants.INTENT_REFUND -> AgentConstants.FSM_REFUND;
            default -> AgentConstants.FSM_CHAT;
        };
    }

    /** 输出二次校验与归一化：非法枚举→chat；低置信→clarify；rag 未开放→chat */
    private String normalizeIntent(IntentDecision decision) {
        String intent = decision.intent();
        if (intent == null || !INTENTS.contains(intent)) {
            return AgentConstants.INTENT_CHAT;
        }
        if (AgentConstants.INTENT_RAG.equals(intent)) {
            return AgentConstants.INTENT_CHAT;
        }
        Double confidence = decision.confidence();
        if (confidence == null || confidence < 0 || confidence > 1) {
            return AgentConstants.INTENT_CHAT;
        }
        if (confidence < agentProperties.getRouter().getConfidenceThreshold()) {
            return AgentConstants.INTENT_CLARIFY;
        }
        return intent;
    }

    /** 会话状态摘要（路由 user 模板槽位）：FSM 状态 + 子状态 */
    private String sessionStateText(Long conversationId) {
        WorkState state = workMemoryStore.getState(conversationId);
        if (AgentConstants.FSM_IDLE.equals(state.getState())) {
            return "无";
        }
        return state.getState() + (state.getAgentSub() == null ? "" : "（" + state.getAgentSub() + "）");
    }

    /** 任务摘要（切换判定槽位）：最近查询条件与车票快照的存在性描述 */
    private String taskSummaryText(Long conversationId) {
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
}
