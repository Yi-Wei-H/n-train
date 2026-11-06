package com.weiyi.agent.server.ai.provider;

import com.anthropic.backends.AnthropicBackend;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientImpl;
import com.anthropic.core.ClientOptions;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.Timeout;
import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.server.entity.AModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.anthropic.http.okhttp.SpringAiAnthropicHttpClient;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;

import java.time.Duration;

/**
 * 创建各类型 Client（官方 SDK + Spring AI OkHttp 封装，配方参考 interview 的 ApiPathResolver）。
 * base_url 经 {@link CorrectBaseURL#correctBaseUrl} 按上游格式归一，
 * 凭证经 {@link ApiAuthInjector#apply} 按认证两维（auth_type + auth_key_name）注入。
 */
@Slf4j
public class AiClient {

    private static final int DEFAULT_CONNECT_TIMEOUT = 10_000;

    /** LLM 长推理场景给足 */
    private static final int DEFAULT_READ_TIMEOUT = 300_000;
    private static final int DEFAULT_Embedding_READ_TIMEOUT = 30_000;

    /** Anthropic API 版本头（官方要求） */
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private AiClient() {
    }

    public static OpenAIClient buildOpenAiClient(AModel model) {
        return buildOpenAiClient(model, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);
    }

    public static OpenAIClient buildOpenAiClientWithEmbedding(AModel model) {
        return buildOpenAiClient(model, DEFAULT_CONNECT_TIMEOUT, DEFAULT_Embedding_READ_TIMEOUT);
    }

    /**
     * OpenAI 兼容系客户端：base 补版本段（openai-java SDK 直接拼 /chat/completions），
     * OpenAI-chat/OpenAI-responses 都走这里
     */
    public static OpenAIClient buildOpenAiClient(AModel model, int connectTimeout, int readTimeout) {
        log.info("正在创建OpenAIClient,ID:{}", model.getId());
        Timeout timeout = Timeout.builder()
                .connect(Duration.ofMillis(connectTimeout))
                .read(Duration.ofMillis(readTimeout))
                .build();
        com.openai.core.ClientOptions.Builder builder = com.openai.core.ClientOptions.Companion.builder()
                .baseUrl(CorrectBaseURL.correctBaseUrl(model.getApiFormat(), model.getBaseUrl()))
                .timeout(timeout)
                .maxRetries(0)
                .httpClient(SpringAiOpenAiHttpClient.builder().timeout(timeout).build());
        ApiAuthInjector.apply(builder, model);
        return new OpenAIClientImpl(builder.build());
    }

    public static AnthropicClient buildAnthropicClient(AModel model) {
        return buildAnthropicClient(model, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);
    }

    /**
     * Anthropic 系客户端：base 去版本段（anthropic-java SDK 自拼 /v1/messages）。
     * 注意 anthropic-java core 的 ClientOptions 没有 apiKey()/credential() 一等方法，
     * 凭证一律走 ApiAuthInjector 的头/参数注入。
     */
    public static AnthropicClient buildAnthropicClient(AModel model, int connectTimeout, int readTimeout) {
        log.info("正在创建AnthropicClient,ID:{}", model.getId());
        com.anthropic.core.Timeout timeout = com.anthropic.core.Timeout.builder()
                .connect(Duration.ofMillis(connectTimeout))
                .read(Duration.ofMillis(readTimeout))
                .build();
        // SpringAiAnthropicHttpClient 必须配 backend（SDK 的序列化/鉴权后端，Spring AI 官方 AnthropicSetup 同款配方）；
        // 不设 backend.apiKey——认证统一走下方 ApiAuthInjector 的头/参数注入
        String baseUrl = CorrectBaseURL.correctBaseUrl(AgentConstants.API_FORMAT_ANTHROPIC, model.getBaseUrl());
        AnthropicBackend backend = AnthropicBackend.builder().baseUrl(baseUrl).build();
        ClientOptions.Builder builder = new ClientOptions.Builder()
                .baseUrl(baseUrl)
                .putHeader("anthropic-version", ANTHROPIC_VERSION)
                .timeout(timeout)
                .maxRetries(0)
                .httpClient(SpringAiAnthropicHttpClient.builder().backend(backend).timeout(timeout).build());
        ApiAuthInjector.apply(builder, model);
        return new AnthropicClientImpl(builder.build());
    }
}
