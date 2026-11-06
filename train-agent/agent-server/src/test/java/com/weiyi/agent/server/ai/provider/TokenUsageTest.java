package com.weiyi.agent.server.ai.provider;

import com.anthropic.models.messages.MessageDeltaUsage;
import com.anthropic.models.messages.OutputTokensDetails;
import com.anthropic.models.messages.ServerToolUsage;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 用量归一口径：Anthropic 的 input_tokens 不含缓存命中（流式末片的原生类型是 MessageDeltaUsage），
 * 只有 OpenAI 的 prompt_tokens 含命中才需要扣减；全路径不允许出现负输入。
 */
class TokenUsageTest {

    @Test
    void anthropicStreamingInputNotReducedByCacheHits() {
        MessageDeltaUsage nativeUsage = MessageDeltaUsage.builder()
                .cacheCreationInputTokens(0L)
                .cacheReadInputTokens(900L)
                .inputTokens(100L)
                .outputTokens(20L)
                .outputTokensDetails(OutputTokensDetails.builder().thinkingTokens(0L).build())
                .serverToolUse(ServerToolUsage.builder().webFetchRequests(0L).webSearchRequests(0L).build())
                .build();
        Usage usage = new DefaultUsage(100, 20, 120, nativeUsage, 900L, 0L);

        TokenUsage tokens = TokenUsage.of(usage);

        assertEquals(100, tokens.input());
        assertEquals(900, tokens.cached());
        assertEquals(20, tokens.output());
    }

    @Test
    void openAiInputSubtractsCacheHits() {
        Usage usage = new DefaultUsage(100, 20, 120, Map.of(), 30L, null);

        assertEquals(70, TokenUsage.of(usage).input());
    }

    @Test
    void openAiInputNeverNegative() {
        Usage usage = new DefaultUsage(100, 20, 120, Map.of(), 900L, null);

        assertEquals(0, TokenUsage.of(usage).input());
    }
}
