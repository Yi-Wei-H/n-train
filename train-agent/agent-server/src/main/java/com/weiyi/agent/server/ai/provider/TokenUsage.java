package com.weiyi.agent.server.ai.provider;

import org.springframework.ai.chat.metadata.Usage;

/**
 * 用量三段口径（a_usage_log）：input 新增输入（未命中缓存的输入）/ cached 缓存命中 / output 输出。
 * Spring AI 2.0 的 Usage 接口透出 cacheRead/cacheWrite，但 promptTokens 语义仍是各上游原生口径，在此归一：
 * <ul>
 *   <li>OpenAI：prompt_tokens 已含命中，input = prompt - cacheRead</li>
 *   <li>Anthropic：input_tokens 不含命中与写缓存，input = input + cacheWrite（新写缓存也属新增处理）</li>
 * </ul>
 * 上游缺字段时对应段为 null，不猜零值。
 */
public record TokenUsage(Integer input, Integer cached, Integer output) {

    public static TokenUsage of(Usage usage) {
        if (usage == null) {
            return new TokenUsage(null, null, null);
        }
        Integer cached = toInt(usage.getCacheReadInputTokens());
        return new TokenUsage(inputOf(usage, cached), cached, usage.getCompletionTokens());
    }

    private static Integer inputOf(Usage usage, Integer cached) {
        Integer prompt = usage.getPromptTokens();
        if (prompt == null) {
            return null;
        }
        if (isAnthropicNative(usage.getNativeUsage())) {
            Long write = usage.getCacheWriteInputTokens();
            return write == null ? prompt : prompt + write.intValue();
        }
        Integer input = cached == null ? prompt : prompt - cached;
        return Math.max(input, 0);
    }

    /**
     * Anthropic 原生长相：非流式是 Usage、流式末片是 MessageDeltaUsage（两者的 input_tokens 都不含缓存命中），
     * 漏判流式会把命中部分再从输入里扣一次，得到负值。
     */
    private static boolean isAnthropicNative(Object nativeUsage) {
        return nativeUsage instanceof com.anthropic.models.messages.Usage
                || nativeUsage instanceof com.anthropic.models.messages.MessageDeltaUsage;
    }

    private static Integer toInt(Long value) {
        return value == null ? null : value.intValue();
    }
}
