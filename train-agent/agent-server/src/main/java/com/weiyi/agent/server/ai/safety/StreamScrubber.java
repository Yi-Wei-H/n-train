package com.weiyi.agent.server.ai.safety;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 流式输出脱敏滑窗：候选闭合机制——待下发文本的结尾若构成敏感形态的完整串或未闭合前缀，
 * 一律扣留暂存，与下一段拼接后重新判定；未闭合的尾部永远不离开暂存，
 * 敏感串闭合（候选字符集外的字符出现）后整段经 OutputSafety 脱敏下发，流结束 flush 兜底。
 * 窗口仅承担推送攒批，防切分职责由尾部候选接管，扣留长度由命中长度决定、不受窗口限制。
 */
public final class StreamScrubber {

    /** 推送攒批缓冲：暂存不足该长度不触发下发（降低 SSE 事件频率） */
    private static final int HOLD = 64;

    /**
     * 尾部候选：与 OutputSafety 规则表对齐——数字串（证件号/手机号，≤18 位）、
     * JWT（点号在候选集内，跨段吞并到闭合）、常见令牌前缀、Bearer 泛化、
     * 数据边界标签与提示词小标题开头。结尾命中即整体扣留。
     */
    private static final Pattern SENSITIVE_TAIL = Pattern.compile(
            "(\\d{1,18}"
                    + "|eyJ[A-Za-z0-9_.-]{0,1024}"
                    + "|sk-[A-Za-z0-9_-]{0,512}"
                    + "|ghp_[A-Za-z0-9]{0,512}"
                    + "|AKIA[0-9A-Z]{0,64}"
                    + "|(?i:bearer\\s+[A-Za-z0-9._+/=-]{0,1024})"
                    + "|<data-boundary[^<]{0,64}"
                    + "|# ?安"
                    + ")$");

    private final StringBuilder pending = new StringBuilder();

    /** 已下发安全文本的全量累积：流结束后作为落库口径，与前端所见严格一致 */
    private final StringBuilder emitted = new StringBuilder();

    /** 喂入一段增量，返回可安全下发的文本（尾部命中的候选整体扣留待下段拼检） */
    public synchronized String feed(String delta) {
        if (delta == null || delta.isEmpty()) {
            return "";
        }
        pending.append(delta);
        int cut = pending.length() - HOLD;
        if (cut <= 0) {
            return "";
        }
        // 结尾命中敏感形态（完整或未闭合前缀）→ 整体退回暂存，不闭合不出门
        Matcher tail = SENSITIVE_TAIL.matcher(pending.substring(0, cut));
        if (tail.find()) {
            cut -= tail.group().length();
        }
        if (cut <= 0) {
            return "";
        }
        String emit = pending.substring(0, cut);
        pending.delete(0, cut);
        return track(OutputSafety.scrub(emit));
    }

    /** 流结束：对剩余暂存做完整脱敏后返回并清空（尾部并入累积） */
    public synchronized String flush() {
        if (pending.isEmpty()) {
            return "";
        }
        String rest = OutputSafety.scrub(pending.toString());
        pending.setLength(0);
        return track(rest);
    }

    /** 已下发安全文本全量累积（含 flush 尾部）：落库与展示同源，不再对原始全量二次脱敏 */
    public synchronized String emittedText() {
        return emitted.toString();
    }

    private String track(String safe) {
        emitted.append(safe);
        return safe;
    }
}
