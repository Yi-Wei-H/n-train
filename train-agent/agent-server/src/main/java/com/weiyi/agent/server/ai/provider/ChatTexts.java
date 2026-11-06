package com.weiyi.agent.server.ai.provider;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;
import java.util.Map;

/**
 * 思考内容过滤：Anthropic 协议把思考与正文拆成独立块——流式下思考增量单独成片并带 thinking 标记，
 * 非流式下思考块单独成 Generation 且排在正文之前（仅带思考签名标记）。
 * 直接取首个 Generation 的文本会在思考模型上拿到思考过程，此处按协议标记跳过思考，
 * 流式增量与一次性调用共用同一口径；与正文混写的内联标签不走本类。
 */
public final class ChatTexts {

    /** 流式思考增量的标记键 */
    private static final String THINKING_KEY = "thinking";

    /** 非流式思考块的标记键（思考签名），正文 Generation 不带该键 */
    private static final String SIGNATURE_KEY = "signature";

    private ChatTexts() {
    }

    /** 流式增量文本：思考片、无文本片（含仅带 usage 的末片）返回 null */
    public static String delta(ChatResponse chunk) {
        if (chunk == null) {
            return null;
        }
        List<Generation> results = chunk.getResults();
        if (results == null || results.isEmpty()) {
            return null;
        }
        Generation generation = results.get(0);
        if (isThinking(generation) || generation.getOutput() == null) {
            return null;
        }
        return generation.getOutput().getText();
    }

    /** 一次性调用取正文：首个非思考 Generation 的文本，全无则空串 */
    public static String text(ChatResponse response) {
        if (response == null || response.getResults() == null) {
            return "";
        }
        for (Generation generation : response.getResults()) {
            if (generation == null || isThinking(generation) || generation.getOutput() == null) {
                continue;
            }
            String text = generation.getOutput().getText();
            if (text != null) {
                return text;
            }
        }
        return "";
    }

    private static boolean isThinking(Generation generation) {
        if (generation == null || generation.getOutput() == null) {
            return false;
        }
        Map<String, Object> metadata = generation.getOutput().getMetadata();
        return metadata != null
                && (Boolean.TRUE.equals(metadata.get(THINKING_KEY)) || metadata.containsKey(SIGNATURE_KEY));
    }
}
