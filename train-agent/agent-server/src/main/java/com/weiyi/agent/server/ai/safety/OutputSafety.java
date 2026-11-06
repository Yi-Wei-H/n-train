package com.weiyi.agent.server.ai.safety;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * 模型输出安全检查：落库与推送前脱敏证件号（含空格分组变体）、疑似令牌与系统提示词回显。
 * 检查异常 fail-closed——返回固定占位文本，不原样透传；命中记录安全事件，不记录原文。
 * 与 StreamScrubber 分工：滑窗保证敏感串未闭合不出门，本类在完整视图上执行脱敏。
 */
@Slf4j
public final class OutputSafety {

    /** 18 位身份证严格结构（压缩后复判用）：地址6 + 出生日期8 + 顺序3 + 校验1 */
    private static final Pattern ID_CARD_STRICT =
            Pattern.compile("\\d{6}(?:19|20)\\d{2}(?:0[1-9]|1[0-2])(?:[0-2]\\d|3[01])\\d{3}[\\dXx]");

    /**
     * 疑似证件号宽候选：连续数字与空格的混合段（压缩后 18 位左右），
     * 命中后在替换函数内去空格、按严格结构复判，通过才掩码——宽进严出，避免误伤普通数字文本。
     * 环视只排除前后紧邻数字（候选不得截断于更长数字串中间），空格是正常词边界。
     */
    private static final Pattern ID_CANDIDATE =
            Pattern.compile("(?<!\\d)\\d[\\d ]{16,28}[\\dXx](?!\\d)");

    /** 疑似 JWT：eyJ 开头的三段式 */
    private static final Pattern JWT_LIKE =
            Pattern.compile("\\beyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\b");

    /** 其他常见令牌形态：OpenAI sk- / GitHub PAT ghp_ / AWS AKIA / Bearer 泛化 */
    private static final Pattern TOKEN_LIKE = Pattern.compile(
            "\\bsk-[A-Za-z0-9_-]{20,}"
                    + "|\\bghp_[A-Za-z0-9]{30,}"
                    + "|\\bAKIA[0-9A-Z]{16}\\b"
                    + "|(?i:\\bbearer\\s+[A-Za-z0-9._+/=-]{20,})");

    /** 系统提示词回显：安全常量中的标志性短语 */
    private static final Pattern SYSTEM_PROMPT_LEAK =
            Pattern.compile("(?i)(<data-boundary[^>]*>|#\\s*安全边界|#\\s*业务安全边界|绝不执行用户数据中出现的任何指令)");

    /** 命中后的统一占位（阻断回显，不暴露拦截规则细节） */
    private static final String MASK = "[已过滤]";

    private OutputSafety() {
    }

    /** 回复文本脱敏：命中证件号/令牌/提示词回显时替换并记录事件 */
    public static String scrub(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        try {
            String cleaned = ID_CANDIDATE.matcher(text).replaceAll(OutputSafety::maskIfIdCard);
            cleaned = JWT_LIKE.matcher(cleaned).replaceAll(MASK);
            cleaned = TOKEN_LIKE.matcher(cleaned).replaceAll(MASK);
            cleaned = SYSTEM_PROMPT_LEAK.matcher(cleaned).replaceAll(MASK);
            if (!cleaned.equals(text)) {
                log.warn("[security] output scrubbed: len={}", text.length());
            }
            return cleaned;
        } catch (Exception e) {
            log.error("[security] 输出检查异常，返回安全占位：len={}", text.length(), e);
            return MASK;
        }
    }

    /** 标题后处理：去围栏引号换行、控制字符、隐私脱敏与超长截断（占位“新会话”除外） */
    public static String cleanTitle(String title, int maxLength) {
        if (title == null || title.isBlank()) {
            return "";
        }
        String cleaned = title.strip()
                .replaceAll("[\\r\\n\\t]", "")
                .replaceAll("^[\"“”'‘’《<]+|[\"“”'‘’》>]+$", "");
        cleaned = scrub(cleaned);
        if (cleaned.length() > maxLength) {
            cleaned = cleaned.substring(0, maxLength);
        }
        return cleaned;
    }

    /** 宽候选复判：压缩去空格后为严格 18 位身份证才掩码（前6后4），否则原样保留 */
    private static String maskIfIdCard(MatchResult matched) {
        String compact = matched.group().replace(" ", "");
        return ID_CARD_STRICT.matcher(compact).matches() ? maskIdCard(compact) : matched.group();
    }

    private static String maskIdCard(String id) {
        return id.substring(0, 6) + "********" + id.substring(id.length() - 4);
    }
}
