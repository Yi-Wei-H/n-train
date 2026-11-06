package com.weiyi.agent.server.ai.provider;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 思考过滤口径：流式思考片带 thinking 标记、非流式思考块带 signature 标记，
 * 正文 Generation 两者都不带；仅携带 usage 的末片无 generation。
 */
class ChatTextsTest {

    @Test
    void deltaSkipsThinkingChunk() {
        ChatResponse chunk = new ChatResponse(List.of(generation("We need", Map.of("thinking", true))));
        assertNull(ChatTexts.delta(chunk));
    }

    @Test
    void deltaPassesTextChunk() {
        ChatResponse chunk = new ChatResponse(List.of(generation("你好", Map.of())));
        assertEquals("你好", ChatTexts.delta(chunk));
    }

    @Test
    void deltaSkipsChunkWithoutGeneration() {
        assertNull(ChatTexts.delta(new ChatResponse(List.of())));
        assertNull(ChatTexts.delta(null));
    }

    @Test
    void textSkipsLeadingThinkingGeneration() {
        ChatResponse response = new ChatResponse(List.of(
                generation("思考过程", Map.of("signature", "sig-1")),
                generation("2", Map.of())));
        assertEquals("2", ChatTexts.text(response));
    }

    @Test
    void textReturnsEmptyWhenOnlyThinking() {
        ChatResponse response = new ChatResponse(List.of(generation("思考过程", Map.of("signature", "sig-1"))));
        assertEquals("", ChatTexts.text(response));
    }

    private Generation generation(String text, Map<String, Object> properties) {
        return new Generation(AssistantMessage.builder().content(text).properties(properties).build());
    }
}
