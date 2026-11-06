package com.weiyi.agent.server.ai.provider;

import com.anthropic.client.AnthropicClient;
import com.openai.client.OpenAIClient;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.redis.constant.RedisKeys;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.common.core.util.JacksonUtil;
import com.weiyi.agent.server.config.AgentProperties;
import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.server.entity.AModel;
import com.weiyi.agent.server.entity.ModelParams;
import com.weiyi.agent.server.service.model.AModelService;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SafeGuardAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型注册管理
 */
@Slf4j
@Component
public class LlmProviderRegistry {

    private final AModelService aModelService;
    private final AgentProperties agentProperties;
    private final ObservationRegistry observationRegistry;

    private final Map<String, AModel> modelCache = new ConcurrentHashMap<>();
    private final Map<String, ChatClient> clientCache = new ConcurrentHashMap<>();
    private final Map<String, ChatClient> rawClientCache = new ConcurrentHashMap<>();
    private final Map<String, ChatModel> chatModelCache = new ConcurrentHashMap<>();
    private final Map<String, EmbeddingModel> embeddingModelCache = new ConcurrentHashMap<>();

    /** 模型配置变更（保存/启停/删除）后失效全部相关缓存，下次取用时按新配置重建客户端 */
    @EventListener
    public void onModelConfigChanged(ModelConfigChangedEvent event) {
        Long modelId = event.modelId();
        String clientKey = RedisKeys.chatClient(modelId);
        modelCache.remove(RedisKeys.modelConfig(modelId));
        clientCache.remove(clientKey);
        rawClientCache.remove(clientKey + ":raw");
        chatModelCache.remove(RedisKeys.chatModel(modelId));
        embeddingModelCache.remove(RedisKeys.embeddingModel(modelId));
        log.info("模型配置缓存已失效：modelId={}", modelId);
    }

    public LlmProviderRegistry(
            AModelService aModelService,
            AgentProperties agentProperties,
            @Autowired(required = false) ObservationRegistry observationRegistry) {
        this.aModelService = aModelService;
        this.agentProperties = agentProperties;
        this.observationRegistry = observationRegistry;
    }

    // 创建ChatClient
    public ChatClient getChatClient(Long id) {
        AModel model = modelCache.computeIfAbsent(RedisKeys.modelConfig(id), key -> {
            log.info("正在获取模型实例,ID:{}", id);
            AModel modelById = aModelService.getModelById(id);
            BizAssert.isTrue(modelById.getModelType() == AgentConstants.MODEL_TYPE_CHAT, ErrorCode.MODEL_TYPE_ERROR);
            return modelById;
        });
        return clientCache.computeIfAbsent(RedisKeys.chatClient(model.getId()), key -> {
            log.info("正在创建ChatClient,ID:{}", model.getId());
            return createChatClient(model, true);
        });
    }

    /**
     * 不挂 SafeGuardAdvisor 的客户端：供回放历史的调用使用。
     * SafeGuardAdvisor 匹配整段 prompt 内容（含历史消息），历史中出现敏感词会把会话每轮都拦成固定安全回复；
     * 回放历史的主回复走入口高危词校验 + 输出脱敏，不依赖 Advisor 输入兜底。
     */
    public ChatClient getChatClientWithoutSafeguard(Long id) {
        AModel model = modelCache.computeIfAbsent(RedisKeys.modelConfig(id), key -> {
            AModel modelById = aModelService.getModelById(id);
            BizAssert.isTrue(modelById.getModelType() == AgentConstants.MODEL_TYPE_CHAT, ErrorCode.MODEL_TYPE_ERROR);
            return modelById;
        });
        return rawClientCache.computeIfAbsent(RedisKeys.chatClient(model.getId()) + ":raw", key -> {
            log.info("正在创建无安全兜底ChatClient,ID:{}", model.getId());
            return createChatClient(model, false);
        });
    }

    /**
     * OpenAI兼容-Responses目前版本不支持，只有升级到Spring AI 2.1+
     * @param model 要构建的模型配置
     * @param safeguard 是否挂 SafeGuardAdvisor（敏感词命中返回固定安全回复）——
     *                  仅限 prompt 不含历史回放的调用（标题、路由、提炼等），见 getChatClientWithoutSafeguard
     * @return 返回一个新的ChatClient实例
     */
    private ChatClient createChatClient(AModel model, boolean safeguard) {
        ChatModel chatModel = getChatModel(model);
        ChatClient.Builder builder = ChatClient.builder(chatModel);
        if (safeguard) {
            builder.defaultAdvisors(SafeGuardAdvisor.builder()
                    .sensitiveWords(agentProperties.getSecurity().getSensitiveWords())
                    .failureResponse(agentProperties.getSecurity().getSafeguardResponse())
                    .build());
        }
        return builder.build();
    }

    private ChatModel getChatModel(AModel model) {
        BizAssert.isTrue(AgentConstants.MODEL_TYPE_CHAT == model.getModelType(), ErrorCode.MODEL_TYPE_ERROR);
        return chatModelCache.computeIfAbsent(RedisKeys.chatModel(model.getId()), key -> {
            log.info("创建ChatModel,模型ID:{}", model.getId());
            return createChatModel(model);
        });
    }

    public EmbeddingModel getEmbeddingModel(Long id) {
        log.info("正在获取向量模型实例,ID:{}", id);
        AModel model = aModelService.getModelById(id);
        BizAssert.isTrue(AgentConstants.MODEL_TYPE_EMBEDDING == model.getModelType(), ErrorCode.MODEL_TYPE_ERROR);
        BizAssert.isTrue(AgentConstants.API_FORMAT_OPENAI_CHAT == model.getApiFormat(), ErrorCode.API_FORMAT_OPENAI_CHAT);
        return embeddingModelCache.computeIfAbsent(RedisKeys.embeddingModel(model.getId()), key -> {
            log.info("创建ChatModel,向量,模型ID:{}", model.getId());
            return createEmbeddingModel(model);
        });
    }

    private EmbeddingModel createEmbeddingModel(AModel model) {
        log.info("正在创建向量模型,模型ID:{}", model.getId());
        OpenAIClient openAIClient = AiClient.buildOpenAiClientWithEmbedding(model);
        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(model.getApiModelId())
                .dimensions(agentProperties.getDimensions())
                .build();
        return OpenAiEmbeddingModel.builder()
                .openAiClient(openAIClient)
                .options(options)
                .observationRegistry(observationRegistry != null ? observationRegistry : ObservationRegistry.NOOP)
                .build();
    }

    private ChatModel createChatModel(AModel model) {
        return switch (model.getApiFormat()) {
            case AgentConstants.API_FORMAT_OPENAI_CHAT -> createOpenAIChatChatModel(model);
            case AgentConstants.API_FORMAT_ANTHROPIC -> createAnthropicChatModel(model);
            default -> {
                log.info("暂不支持该上游格式:{},模型ID:{}", model.getApiFormat(), model.getId());
                throw new BusinessException(ErrorCode.MODEL_NOT_PROVIDE);
            }
        };
    }

    private ChatModel createAnthropicChatModel(AModel model) {
        log.info("正在创建AnthropicChatModel,ID:{}", model.getId());
        AnthropicClient anthropicClient = AiClient.buildAnthropicClient(model);
        ModelParams modelParams = parseParams(model);
        AnthropicChatOptions.Builder builder = AnthropicChatOptions.builder()
                .model(model.getApiModelId())
                .temperature(modelParams != null && modelParams.getTemperature() != null ? modelParams.getTemperature() : agentProperties.getTemperature());
        // max_tokens 为 Anthropic 必填项，参数快照未配置时用兜底值
        builder.maxTokens(modelParams != null && modelParams.getMaxTokens() != null
                ? modelParams.getMaxTokens() : agentProperties.getDefaultAnthropicMaxTokens());
        if (modelParams != null) {
            if (modelParams.getTopP() != null) {
                builder.topP(modelParams.getTopP());
            }
            if (modelParams.getTopK() != null) {
                builder.topK(modelParams.getTopK());
            }
            if (modelParams.getStopSequences() != null) {
                builder.stopSequences(modelParams.getStopSequences());
            }
            // 思考开关（业界 thinking.type 形态）：disabled 一等关闭；enabled 需配预算（≥1024 且 < maxTokens，Anthropic 约束）
            if ("disabled".equalsIgnoreCase(modelParams.getThinkingType())) {
                builder.thinkingDisabled();
            } else if (modelParams.getThinkingBudgetTokens() != null) {
                builder.thinkingEnabled(modelParams.getThinkingBudgetTokens());
            }
            if (modelParams.getDisableParallelToolUse() != null) {
                builder.disableParallelToolUse(modelParams.getDisableParallelToolUse());
            }
        }
        AnthropicChatOptions options = builder.build();

        return AnthropicChatModel.builder()
                .anthropicClient(anthropicClient)
                .anthropicClientAsync(anthropicClient.async())
                .observationRegistry(observationRegistry != null ? observationRegistry : ObservationRegistry.NOOP)
                .options(options)
                .build();
    }


    private ChatModel createOpenAIChatChatModel(AModel model) {
        log.info("正在创建OpenAIChatChatModel,ID:{}", model.getId());
        OpenAIClient openAIClient = AiClient.buildOpenAiClient(model);
        ModelParams modelParams = parseParams(model);

        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(model.getApiModelId())
                .temperature(modelParams != null && modelParams.getTemperature() != null ? modelParams.getTemperature() : agentProperties.getTemperature());
        if (modelParams != null) {
            // o 系推理模型只认 max_completion_tokens，设置后优先于 maxTokens
            if (modelParams.getMaxCompletionTokens() != null) {
                builder.maxCompletionTokens(modelParams.getMaxCompletionTokens());
            } else if (modelParams.getMaxTokens() != null) {
                builder.maxTokens(modelParams.getMaxTokens());
            }
            if (modelParams.getTopP() != null) {
                builder.topP(modelParams.getTopP());
            }
            if (modelParams.getFrequencyPenalty() != null) {
                builder.frequencyPenalty(modelParams.getFrequencyPenalty());
            }
            if (modelParams.getPresencePenalty() != null) {
                builder.presencePenalty(modelParams.getPresencePenalty());
            }
            if (modelParams.getSeed() != null) {
                builder.seed(modelParams.getSeed());
            }
            if (modelParams.getStopSequences() != null) {
                builder.stop(modelParams.getStopSequences());
            }
            if (modelParams.getParallelToolCalls() != null) {
                builder.parallelToolCalls(modelParams.getParallelToolCalls());
            }
            if (modelParams.getVerbosity() != null) {
                builder.verbosity(modelParams.getVerbosity());
            }
            if (modelParams.getReasoningEffort() != null) {
                builder.reasoningEffort(modelParams.getReasoningEffort());
            }
            // 厂商非标参数透传（thinking 开关/enable_thinking/非标 effort 取值等），键名取值由配置人按厂商文档保证
            if (modelParams.getExtraBody() != null && !modelParams.getExtraBody().isEmpty()) {
                builder.extraBody(modelParams.getExtraBody());
            }
        }
        OpenAiChatOptions options = builder.build();

        return OpenAiChatModel.builder()
                .openAiClient(openAIClient)
                .openAiClientAsync(openAIClient.async())
                .observationRegistry(observationRegistry != null ? observationRegistry : ObservationRegistry.NOOP)
                .options(options)
                .build();
    }

    /**
     * 解析 a_model.params 参数快照（JSON 字符串，键名同 ModelParams 字段）。
     * params 为空或 JSON 非法时返回 null（JacksonUtil 内部告警），由调用方回退全局默认
     */
    private ModelParams parseParams(AModel model) {
        return JacksonUtil.fromJson(model.getParams(), ModelParams.class);
    }
}
