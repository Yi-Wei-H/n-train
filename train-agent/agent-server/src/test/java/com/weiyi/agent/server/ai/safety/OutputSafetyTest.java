package com.weiyi.agent.server.ai.safety;
import com.weiyi.agent.server.ai.safety.OutputSafety;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutputSafetyTest {

    @Test
    @DisplayName("完整证件号脱敏为前6后4，其余内容保留")
    void idCardMasked() {
        String scrubbed = OutputSafety.scrub("旅客证件号 11010119900307863X 请查收");
        assertFalse(scrubberContainsRaw(scrubbed));
        assertTrue(scrubbed.startsWith("旅客证件号 110101********863X"));
    }

    @Test
    @DisplayName("疑似 JWT 令牌与系统提示词回显被替换")
    void tokenAndPromptLeakMasked() {
        assertTrue(OutputSafety.scrub("token=eyJhbGciOi.eyJzdWIiOiJ9.SflKxwRJ").contains("[已过滤]"));
        assertTrue(OutputSafety.scrub("规则是 # 安全边界 里的内容").contains("[已过滤]"));
    }

    @Test
    @DisplayName("正常文本不受影响")
    void normalTextUntouched() {
        assertEquals("明天去北京的车票还有吗", OutputSafety.scrub("明天去北京的车票还有吗"));
        // 普通数字文本（压缩后非严格证件结构）原样保留
        assertEquals("编号 123456 789012 3456", OutputSafety.scrub("编号 123456 789012 3456"));
    }

    @Test
    @DisplayName("空格分组的证件号变体同样掩码")
    void spacedIdCardMasked() {
        String scrubbed = OutputSafety.scrub("号码 110101 19900307 863X 请查收");
        assertFalse(scrubbed.contains("19900307"));
        assertTrue(scrubbed.contains("110101********863X"));
    }

    @Test
    @DisplayName("非 eyJ 令牌（sk-/ghp_/AKIA/Bearer）被替换")
    void otherTokenShapesMasked() {
        assertTrue(OutputSafety.scrub("key=sk-proj-AbCdEfGh1234567890IjKlMnOp").contains("[已过滤]"));
        assertTrue(OutputSafety.scrub("pat=ghp_" + "a1B2c3D4e5F6g7H8i9J0k1L2m3N4o5P6q7R8").contains("[已过滤]"));
        assertTrue(OutputSafety.scrub("aws=AKIAIOSFODNN7EXAMPLE").contains("[已过滤]"));
        assertTrue(OutputSafety.scrub("Authorization: Bearer abc123def456ghi789jkl012").contains("[已过滤]"));
    }

    @Test
    @DisplayName("标题清洗：去引号换行、控制字符与超长截断")
    void titleCleaned() {
        assertEquals("查北京车票", OutputSafety.cleanTitle("“查北京车票”\n", 20));
        assertTrue(OutputSafety.cleanTitle("一二三四五六七八九十一二三四五六七八九十一", 20).length() == 20);
        assertEquals("", OutputSafety.cleanTitle(null, 20));
    }

    private boolean scrubberContainsRaw(String text) {
        return text.contains("11010119900307863X");
    }
}
