package com.weiyi.agent.server.ai.safety;

/**
 * 思考块剥离：部分思考型模型不接受关闭参数（或厂商开关字段名不被识别），思考过程以
 * {@code <think>...</think>} 包裹混在正文里输出（Qwen3 / DeepSeek-R1 系等常见形态）。
 * 仅剥离**回复开头**的思考块——中途出现的同名标签多为用户内容回显或代码示例，不动。
 * <p>流式状态机：PROBING（缓冲开头判定是否思考块）→ STRIPPING（吞思考，尾窗防 {@code </think>}
 * 跨段）→ PASSING（正文直通）；标签闭合后的内容（含首个换行）放行，流结束仍未闭合则整段视为思考丢弃。
 * 落库口径与推送一致：喂入本剥离器的整轮文本经 {@link #flush()} 收尾后即为最终正文。
 */
public final class ThinkTagStripper {

    private static final String OPEN = "<think>";
    private static final String CLOSE = "</think>";

    private enum State { PROBING, STRIPPING, PASSING }

    private State state = State.PROBING;
    private final StringBuilder head = new StringBuilder();
    /** 剥离期尾窗：保留末尾 CLOSE.length-1 字符，防闭合标签被 delta 切分 */
    private final StringBuilder tail = new StringBuilder();

    /** 流式喂入：返回本次可下发的正文（可为空串），状态机内部吞掉思考段 */
    public synchronized String feed(String delta) {
        if (delta == null || delta.isEmpty()) {
            return "";
        }
        return switch (state) {
            case PASSING -> delta;
            case PROBING -> probe(delta);
            case STRIPPING -> strip(delta);
        };
    }

    /** 流结束收尾：判定期未决按无思考放行；剥离期未闭合按全思考丢弃 */
    public synchronized String flush() {
        switch (state) {
            case PROBING -> {
                state = State.PASSING;
                return head.toString();
            }
            case STRIPPING -> {
                state = State.PASSING;
                return tail.indexOf(CLOSE) >= 0 ? afterClose(tail.toString()) : "";
            }
            default -> {
                return "";
            }
        }
    }

    private String probe(String delta) {
        head.append(delta);
        String trimmed = head.toString().stripLeading();
        if (trimmed.startsWith(OPEN)) {
            state = State.STRIPPING;
            head.setLength(0);
            // OPEN 之后可能本段就带了闭合
            String rest = trimmed.substring(OPEN.length());
            return strip(rest);
        }
        if (trimmed.length() < OPEN.length() && OPEN.startsWith(trimmed)) {
            return ""; // 仍是 <think> 的可能前缀，继续缓冲
        }
        state = State.PASSING;
        return head.toString();
    }

    private String strip(String delta) {
        tail.append(delta);
        int idx = tail.indexOf(CLOSE);
        if (idx < 0) {
            // 未闭合：丢弃确定已过的部分，仅保留可能被切分的闭合前缀
            int keep = CLOSE.length() - 1;
            if (tail.length() > keep) {
                tail.delete(0, tail.length() - keep);
            }
            return "";
        }
        state = State.PASSING;
        String out = afterClose(tail.substring(idx));
        tail.setLength(0);
        return out;
    }

    /** 闭合标签之后的内容，剥掉紧随的首个换行（思考块后正文通常另起一行） */
    private String afterClose(String fromClose) {
        String rest = fromClose.substring(CLOSE.length());
        if (rest.startsWith("\r\n")) {
            return rest.substring(2);
        }
        if (rest.startsWith("\n")) {
            return rest.substring(1);
        }
        return rest;
    }

    /**
     * 非流式整段剥离（标题生成等一次性调用复用）：开头思考块剥掉返回其后正文，
     * 未闭合返回空串，无思考块原样返回。流式场景请用 feed/flush 状态机。
     */
    public static String stripLeading(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        String trimmed = text.stripLeading();
        if (!trimmed.startsWith(OPEN)) {
            return text;
        }
        int idx = trimmed.indexOf(CLOSE);
        if (idx < 0) {
            return "";
        }
        String rest = trimmed.substring(idx + CLOSE.length());
        return rest.startsWith("\r\n") ? rest.substring(2)
                : rest.startsWith("\n") ? rest.substring(1) : rest;
    }
}
