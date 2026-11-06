package com.weiyi.agent.server.ai.provider;
import com.weiyi.agent.server.ai.provider.ModelListFetcher;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型列表获取纯逻辑单测：候选 URL 生成、宽松解析、错误体脱敏截断（无网络无数据库）。
 */
class ModelListFetcherTest {

    @Test
    @DisplayName("候选生成：裸根地址拼 /v1/models")
    void testCandidatesPlainRoot() {
        assertEquals(List.of("https://api.siliconflow.cn/v1/models"),
                ModelListFetcher.buildCandidates("https://api.siliconflow.cn"));
    }

    @Test
    @DisplayName("候选生成：去尾斜杠")
    void testCandidatesTrailingSlash() {
        assertEquals(List.of("https://api.example.com/v1/models"),
                ModelListFetcher.buildCandidates("https://api.example.com/"));
    }

    @Test
    @DisplayName("候选生成：/v1 结尾不再补 /v1")
    void testCandidatesWithV1() {
        assertEquals(List.of("https://api.example.com/v1/models"),
                ModelListFetcher.buildCandidates("https://api.example.com/v1"));
    }

    @Test
    @DisplayName("候选生成：非 /v1 版本段（智谱 /v4）首选 {base}/models，保留 /v1/models 次候选")
    void testCandidatesVersionSegmentV4() {
        assertEquals(List.of(
                        "https://open.bigmodel.cn/api/coding/paas/v4/models",
                        "https://open.bigmodel.cn/api/coding/paas/v4/v1/models"),
                ModelListFetcher.buildCandidates("https://open.bigmodel.cn/api/coding/paas/v4"));
    }

    @Test
    @DisplayName("候选生成：兼容子路径 /anthropic 剥离后追加根地址候选（DeepSeek 场景）")
    void testCandidatesStripAnthropic() {
        assertEquals(List.of(
                        "https://api.deepseek.com/anthropic/v1/models",
                        "https://api.deepseek.com/v1/models",
                        "https://api.deepseek.com/models"),
                ModelListFetcher.buildCandidates("https://api.deepseek.com/anthropic"));
    }

    @Test
    @DisplayName("候选生成：无已知后缀不剥离（OpenRouter /api）")
    void testCandidatesNoStrip() {
        assertEquals(List.of("https://openrouter.ai/api/v1/models"),
                ModelListFetcher.buildCandidates("https://openrouter.ai/api"));
    }

    @Test
    @DisplayName("解析：OpenAI 形状 data[].{id, owned_by}")
    void testParseOpenAiShape() {
        List<com.weiyi.agent.api.dto.ModelItemDTO> models = ModelListFetcher.parseModels(
                "{\"object\":\"list\",\"data\":[{\"id\":\"gpt-4\",\"object\":\"model\",\"owned_by\":\"openai\"}]}");
        assertNotNull(models);
        assertEquals(1, models.size());
        assertEquals("gpt-4", models.get(0).getId());
        assertEquals("openai", models.get(0).getOwnedBy());
    }

    @Test
    @DisplayName("解析：Anthropic 形状 data[].{id, display_name}，owned_by 恒空")
    void testParseAnthropicShape() {
        List<com.weiyi.agent.api.dto.ModelItemDTO> models = ModelListFetcher.parseModels(
                "{\"data\":[{\"type\":\"model\",\"id\":\"claude-x\",\"display_name\":\"Claude X\"}],\"has_more\":false}");
        assertNotNull(models);
        assertEquals(1, models.size());
        assertEquals("claude-x", models.get(0).getId());
        assertNull(models.get(0).getOwnedBy());
        assertEquals("Claude X", models.get(0).getDisplayName());
    }

    @Test
    @DisplayName("解析：智谱 models[].slug 优先、id 回退，杂项跳过")
    void testParseZhipuShape() {
        List<com.weiyi.agent.api.dto.ModelItemDTO> models = ModelListFetcher.parseModels(
                "{\"models\":[{\"slug\":\"glm-5.3\"},{\"id\":\"openai-shaped\"},{\"name\":\"neither\"},{}]}");
        assertNotNull(models);
        assertEquals(List.of("glm-5.3", "openai-shaped"),
                models.stream().map(com.weiyi.agent.api.dto.ModelItemDTO::getId).toList());
    }

    @Test
    @DisplayName("解析：data 与 models 并存时 data 优先（cc-switch #7593 教训）")
    void testParseDataPriority() {
        List<com.weiyi.agent.api.dto.ModelItemDTO> models = ModelListFetcher.parseModels(
                "{\"data\":[{\"id\":\"model-a\"}],\"models\":[{\"slug\":\"should-be-ignored\"}]}");
        assertNotNull(models);
        assertEquals(1, models.size());
        assertEquals("model-a", models.get(0).getId());
    }

    @Test
    @DisplayName("解析：空 id 跳过；data 空数组是合法空列表")
    void testParseSkipBlankId() {
        List<com.weiyi.agent.api.dto.ModelItemDTO> models = ModelListFetcher.parseModels(
                "{\"data\":[{\"id\":\"\"},{\"id\":123},{\"id\":\"ok\"}]}");
        assertNotNull(models);
        assertEquals(1, models.size());
        assertEquals("ok", models.get(0).getId());

        assertEquals(0, ModelListFetcher.parseModels("{\"data\":[]}").size());
    }

    @Test
    @DisplayName("解析：既无 data 又无 models / 非法 JSON → null（降级信号）")
    void testParseUnknownShape() {
        assertNull(ModelListFetcher.parseModels("{\"foo\":1}"));
        assertNull(ModelListFetcher.parseModels("not json at all"));
        assertNull(ModelListFetcher.parseModels("{\"data\":{\"not\":\"array\"}}"));
    }

    @Test
    @DisplayName("脱敏：apiKey 明文替换为 [REDACTED]，超 512 字符截断")
    void testRedact() {
        assertEquals("invalid [REDACTED] here",
                ModelListFetcher.redact("invalid sk-secret-key here", "sk-secret-key"));
        assertEquals("", ModelListFetcher.redact(null, "sk-secret-key"));
        String longBody = "x".repeat(600);
        String redacted = ModelListFetcher.redact(longBody, "sk-secret-key");
        assertEquals(512 + 1, redacted.length());
        assertTrue(redacted.endsWith("…"));
    }
}
