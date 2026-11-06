package com.weiyi.agent.server.ai.safety;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流式脱敏滑窗：候选闭合机制——尾部命中敏感形态（完整或未闭合前缀）一律扣留，
 * 闭合后整段经 OutputSafety 脱敏，flush 全量兜底。
 */
class StreamScrubberTest {

    private static final String ID = "110105199001011234";

    @Test
    @DisplayName("身份证压在切分点：完整或部分尾部一律扣留，首段数字不外泄")
    void idCardAtCutBoundaryHeld() {
        StreamScrubber scrubber = new StreamScrubber();
        // 布局 50 填充 + 18 位身份证 + 47 尾文，使 cut 恰落在身份证第 1 位之后
        String filler = "前".repeat(50);
        String round = scrubber.feed(filler + ID + "后".repeat(47));
        assertEquals(filler, round, "切点上的第一位数字应被扣留，仅发出填充文本");

        StringBuilder received = new StringBuilder(round);
        received.append(scrubber.feed("追加文本继续推进窗口。".repeat(10)));
        received.append(scrubber.flush());
        assertFalse(received.toString().contains(ID), "任何一段都不得含原号");
        assertTrue(received.toString().contains("110105********1234"), "完整形态最终以掩码展示");
    }

    @Test
    @DisplayName("超窗口长度的令牌残片被整体扣留，闭合后整段脱敏")
    void longJwtHeldUntilClosed() {
        StreamScrubber scrubber = new StreamScrubber();
        String jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
                + ".eyJzdWIiOiIxMjM0NTY3ODkwIn0"
                + ".dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U";
        StringBuilder received = new StringBuilder();
        received.append(scrubber.feed("令牌开始 " + jwt.substring(0, 80)));
        assertFalse(received.toString().contains("eyJ"), "令牌前缀不得随首段外泄");
        received.append(scrubber.feed(jwt.substring(80) + " 令牌结束"));
        received.append(scrubber.feed("后续".repeat(40)));
        received.append(scrubber.flush());
        assertFalse(received.toString().contains(jwt), "完整令牌不得出现");
        assertFalse(received.toString().contains("eyJhbGciOiJIUzI1NiIs"), "残片拼接也不得出现");
    }

    @Test
    @DisplayName("长数字流不卡死：仅扣留结尾未闭合部分，前段正常流出")
    void longDigitStreamFlows() {
        StreamScrubber scrubber = new StreamScrubber();
        StringBuilder received = new StringBuilder();
        received.append(scrubber.feed("123456789012345678901234567890"));
        received.append(scrubber.feed("字".repeat(64)));
        received.append(scrubber.flush());
        assertTrue(received.toString().startsWith("123456789012"), "前 12 位数字应正常流出");
    }

    @Test
    @DisplayName("流结束兜底：暂存中的未闭合残片经全量脱敏后发出")
    void flushScrubsRemainder() {
        StreamScrubber scrubber = new StreamScrubber();
        String out = scrubber.feed("身份信息 " + ID);
        assertEquals("", out, "不足攒批缓冲，首轮不下发");
        String flushed = scrubber.flush();
        assertFalse(flushed.contains(ID));
        assertTrue(flushed.contains("110105********1234"));
    }
}
