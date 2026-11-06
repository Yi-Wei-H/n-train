package com.weiyi.agent.server.ai.prompt;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * 自由文本进模型前的注入清洗：把伪造的指令载体替换为中性标记，不能伪装成指令载体即达标，
 * 业务事实不做删改；数据外层再套随机边界标签。
 * 只对不可信数据段使用（输入、历史、记忆、摘要、Tool 文本）；模板静态内容与服务端状态不过此处。
 * 命中只记录类型与文本长度，不记录原文；清洗异常 fail-closed，不回退原文拼接。
 */
@Slf4j
public final class PromptSanitizer {

    /** 伪造的 data-boundary 标签（含属性变体），替换为中性标记 */
    private static final Pattern FAKE_BOUNDARY =
            Pattern.compile("(?i)</?data-boundary-[a-z0-9-]{1,40}[^>]*>");

    /** 行首的角色标记（含中文冒号），替换后保留行内容 */
    private static final Pattern ROLE_LINE =
            Pattern.compile("(?im)^\\s*(system|assistant|user|human|ai|model)\\s*[:：]");

    /** 本项目静态数据分隔符的伪造形态（---xx内容开始/结束---） */
    private static final Pattern FAKE_DELIMITER =
            Pattern.compile("(?m)-{2,}[^\\n]{0,12}内容(开始|结束)-{2,}");

    /** 明确注入短语（完整短语级命中，不做单词级宽泛匹配防误伤），大小写不敏感 */
    private static final Pattern[] INJECTION_PHRASES = {
            Pattern.compile("(?i)ignore (all )?previous instructions"),
            Pattern.compile("(?i)ignore (all )?previous prompts"),
            Pattern.compile("(?i)forget (all )?instructions"),
            Pattern.compile("(?i)disregard (all )?previous instructions"),
            Pattern.compile("(?i)you are no longer"),
            Pattern.compile("(?i)act as (an?|the) .{0,20}(system|developer|admin)"),
            Pattern.compile("忽略之前(的)?(所有)?指令"),
            Pattern.compile("忽略以上(所有)?指令"),
            Pattern.compile("忽略前面(的)?(所有)?指令"),
            Pattern.compile("无视之前(的)?(所有)?指令"),
            Pattern.compile("你不再是"),
            Pattern.compile("(输出|打印|透露|泄露)(你的)?(系统)?提示词")
    };

    private static final String MARK_BOUNDARY = "[filtered-boundary-tag]";
    private static final String MARK_ROLE = "[filtered-role-marker]";
    private static final String MARK_DELIMITER = "[filtered-delimiter]";
    private static final String MARK_PHRASE = "[filtered]";

    private PromptSanitizer() {
    }

    /** 消毒数据：命中即替换为中性标记并记录安全事件（类型+长度），异常时终止而非回退原文 */
    public static String sanitize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        try {
            String cleaned = replaceAll(FAKE_BOUNDARY, text, MARK_BOUNDARY);
            cleaned = replaceAll(ROLE_LINE, cleaned, MARK_ROLE);
            cleaned = replaceAll(FAKE_DELIMITER, cleaned, MARK_DELIMITER);
            for (Pattern phrase : INJECTION_PHRASES) {
                cleaned = replaceAll(phrase, cleaned, MARK_PHRASE);
            }
            return cleaned;
        } catch (Exception e) {
            log.error("[security] sanitize 异常，终止数据装配：len={}", text.length(), e);
            throw new IllegalStateException("数据净化失败，已终止拼接", e);
        }
    }

    private static String replaceAll(Pattern pattern, String text, String marker) {
        String cleaned = pattern.matcher(text).replaceAll(marker);
        if (!cleaned.equals(text)) {
            log.warn("[security] sanitize hit: type={}, len={}", marker, text.length());
        }
        return cleaned;
    }

    /** 每次装配生成随机边界标签，防止历史内容伪造固定标签提升为指令 */
    public static String nextBoundaryTag(String name) {
        String uuid8 = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "data-boundary-" + uuid8 + "-" + name;
    }
}
