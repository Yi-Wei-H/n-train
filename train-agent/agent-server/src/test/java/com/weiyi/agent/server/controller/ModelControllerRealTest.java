package com.weiyi.agent.server.controller;
import com.weiyi.agent.server.ai.provider.AiClient;

import com.weiyi.agent.server.ai.provider.LlmProviderRegistry;
import com.weiyi.agent.server.config.AgentProperties;
import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.api.dto.ModelTestDTO;
import com.weiyi.agent.server.entity.AModel;
import com.weiyi.agent.server.service.model.AModelService;
import com.weiyi.agent.server.service.model.ModelTestService;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.agent.server.controller.rpc.ModelController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * chat 模型连通性的真实测试：全部用例发起真实 HTTPS 请求到 DeepSeek
 * （OpenAI 兼容端点 https://api.deepseek.com 与 Anthropic 兼容端点 /anthropic 各一组）。
 * 全链路真实执行（ModelController → ModelTestService → LlmProviderRegistry → AiClient →
 * 官方 SDK → DeepSeek），无 Mockito / MockMvc 模拟；
 * 仅"从数据库读模型配置"一步换成内存数据源（本测试不依赖数据库）。
 * 成功用例消耗极少量 token；失败用例在计费前被上游拒绝（401/404/DNS），不消耗 token。
 */
class ModelControllerRealTest {

    /** DeepSeek 平台有效 key，两种格式端点通用 */
    private static final String DEEPSEEK_API_KEY = "5d61b2e8879";

    @Test
    @DisplayName("OpenAI 格式端点连通成功")
    void testOpenAiFormatSuccess() {
        ModelTestDTO dto = runTest(deepSeekChat());
        assertTrue(dto.getSuccess(), () -> "连通失败: " + dto.getErrMsg());
    }

    @Test
    @DisplayName("Anthropic 格式端点连通成功")
    void testAnthropicFormatSuccess() {
        ModelTestDTO dto = runTest(deepSeekAnthropic());
        assertTrue(dto.getSuccess(), () -> "连通失败: " + dto.getErrMsg());
    }

    @Test
    @DisplayName("参数快照生效：temperature/maxTokens 覆盖全局默认（Anthropic 必填 max_tokens 走快照值）")
    void testParamsSnapshot() {
        AModel model = deepSeekAnthropic();
        model.setParams("{\"temperature\":0.1,\"maxTokens\":64}");
        ModelTestDTO dto = runTest(model);
        assertTrue(dto.getSuccess(), () -> "连通失败: " + dto.getErrMsg());
    }

    @Test
    @DisplayName("无效 key：真实 401 归类为 API Key 无效")
    void testInvalidKey() {
        AModel model = deepSeekChat();
        model.setApiKey("sk-invalid-key-for-classification-test");
        ModelTestDTO dto = runTest(model);
        assertFalse(dto.getSuccess(), "无效 key 不应连通成功");
        assertTrue(dto.getErrMsg().startsWith("API Key 无效或未授权"), () -> "实际归类: " + dto.getErrMsg());
    }

    @Test
    @DisplayName("模型 id 错误：真实 404 归类为模型不存在或路径错误")
    void testWrongModelId() {
        AModel model = deepSeekChat();
        model.setApiModelId("no-such-model-xyz");
        ModelTestDTO dto = runTest(model);
        assertFalse(dto.getSuccess(), "不存在的模型不应连通成功");
        assertTrue(dto.getErrMsg().startsWith("模型不存在或路径错误"), () -> "实际归类: " + dto.getErrMsg());
    }

    @Test
    @DisplayName("base_url 错误：真实 DNS 失败归类为网络异常")
    void testBadBaseUrl() {
        AModel model = deepSeekChat();
        model.setBaseUrl("https://api.deepseek.invalid");
        ModelTestDTO dto = runTest(model);
        assertFalse(dto.getSuccess(), "不存在的域名不应连通成功");
        assertTrue(dto.getErrMsg().startsWith("网络异常"), () -> "实际归类: " + dto.getErrMsg());
    }

    @Test
    @DisplayName("不支持的组合单独分错：Responses/Gemini 格式无客户端实现，构建时抛 45003 而非生成连通结果")
    void testUnsupportedApiFormat() {
        for (int apiFormat : List.of(AgentConstants.API_FORMAT_OPENAI_RESPONSES, AgentConstants.API_FORMAT_GEMINI)) {
            AModel model = deepSeekChat();
            model.setApiFormat(apiFormat);
            BusinessException ex = assertThrows(BusinessException.class, () -> runTest(model));
            assertEquals(ErrorCode.MODEL_NOT_PROVIDE.getCode(), ex.getCode());
        }
    }

    @Test
    @DisplayName("OpenAI 格式 × 非 Bearer 认证：SDK 构建即抛 IllegalStateException（保存层已禁止该组合，此处锁定绕过保存层的边界行为）")
    void testOpenAiNonBearerRejectedBySdk() {
        AModel model = deepSeekChat();
        model.setAuthType(AgentConstants.AUTH_HEADER);
        model.setAuthKeyName("x-api-key");
        assertThrows(IllegalStateException.class, () -> runTest(model));
    }

    @Test
    @DisplayName("Anthropic 格式 × Header 认证（x-api-key 直传）真实连通：两维正交在 Anthropic 侧成立")
    void testAnthropicHeaderAuth() {
        AModel model = deepSeekAnthropic();
        model.setAuthType(AgentConstants.AUTH_HEADER);
        model.setAuthKeyName("x-api-key");
        ModelTestDTO dto = runTest(model);
        assertTrue(dto.getSuccess(), () -> "连通失败: " + dto.getErrMsg());
    }

    @Test
    @DisplayName("Anthropic 格式 × Query 认证不匹配上游：真实 401 归入 Key 无效类")
    void testAuthQueryMismatch() {
        AModel model = deepSeekAnthropic();
        model.setAuthType(AgentConstants.AUTH_QUERY);
        model.setAuthKeyName("api_key");
        ModelTestDTO dto = runTest(model);
        assertFalse(dto.getSuccess(), "Anthropic 协议无查询参数认证，不应连通成功");
        assertTrue(dto.getErrMsg().startsWith("API Key 无效或未授权"), () -> "实际归类: " + dto.getErrMsg());
    }

    private ModelTestDTO runTest(AModel model) {
        // 内存数据源：覆盖唯一的数据库访问点，链路上其余依赖零替换
        AModelService modelService = new AModelService(null, null) {
            @Override
            public AModel getModelById(Long id) {
                return model;
            }
        };
        LlmProviderRegistry registry = new LlmProviderRegistry(modelService, new AgentProperties(), null);
        ModelTestService testService = new ModelTestService(modelService, registry);
        ModelTestDTO result = new ModelController(null, testService, null).test(model.getId());
        System.out.println("连通结果: " + result);
        return result;
    }

    /** 默认 DeepSeek OpenAI 兼容配置，各用例按需覆盖字段 */
    private AModel deepSeekChat() {
        AModel model = new AModel();
        model.setId(1L);
        model.setModelName("DeepSeek Chat");
        model.setModelType(AgentConstants.MODEL_TYPE_CHAT);
        model.setBaseUrl("https://api.deepseek.com");
        model.setApiFormat(AgentConstants.API_FORMAT_OPENAI_CHAT);
        model.setAuthType(AgentConstants.AUTH_BEARER);
        model.setApiModelId("deepseek-chat");
        model.setApiKey(DEEPSEEK_API_KEY);
        return model;
    }

    /** DeepSeek Anthropic 兼容端点配置 */
    private AModel deepSeekAnthropic() {
        AModel model = deepSeekChat();
        model.setBaseUrl("https://api.deepseek.com/anthropic");
        model.setApiFormat(AgentConstants.API_FORMAT_ANTHROPIC);
        return model;
    }
}
