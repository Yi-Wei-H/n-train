package com.weiyi.agent.server.controller;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 向量模型连通性的真实测试：全部用例发起真实 HTTPS 请求到阿里百炼（OpenAI 兼容端点）。
 * 全链路真实执行（ModeTestController → ModelTestService.testEmbedding → LlmProviderRegistry
 * → OpenAiEmbeddingModel → 百炼 /embeddings），无 Mockito / MockMvc 模拟；
 * 仅"从数据库读模型配置"一步换成内存数据源（本测试不依赖数据库）。
 * embed("ping") 消耗极少量 token；失败用例在计费前被上游拒绝，不消耗 token。
 */
class ModelEmbeddingRealTest {

    /** 阿里百炼平台有效 key */
    private static final String API_KEY = "wNLRgBFxQf10rsrIb7RD5-LnC54";

    @Test
    @DisplayName("百炼向量模型真实连通：返回向量维度")
    void testEmbeddingSuccess() {
        ModelTestDTO dto = runTest(bailianEmbedding());
        assertTrue(dto.getSuccess(), () -> "连通失败: " + dto.getErrMsg());
        assertTrue(dto.getDimension() > 0, "连通成功时 dimension 应有值");
    }

    @Test
    @DisplayName("无效 key：真实 401 归类为 API Key 无效")
    void testInvalidKey() {
        AModel model = bailianEmbedding();
        model.setApiKey("sk-invalid-key-for-classification-test");
        ModelTestDTO dto = runTest(model);
        assertFalse(dto.getSuccess(), "无效 key 不应连通成功");
        assertTrue(dto.getErrMsg().startsWith("API Key 无效或未授权"), () -> "实际归类: " + dto.getErrMsg());
    }

    @Test
    @DisplayName("模型 id 错误：真实上游报错归类为模型不存在或路径错误")
    void testWrongModelId() {
        AModel model = bailianEmbedding();
        model.setApiModelId("no-such-embedding-model");
        ModelTestDTO dto = runTest(model);
        assertFalse(dto.getSuccess(), "不存在的模型不应连通成功");
        assertTrue(dto.getErrMsg().startsWith("模型不存在或路径错误"), () -> "实际归类: " + dto.getErrMsg());
    }

    @Test
    @DisplayName("base_url 错误：真实 DNS 失败归类为网络异常")
    void testBadBaseUrl() {
        AModel model = bailianEmbedding();
        model.setBaseUrl("https://dashscope.invalid");
        ModelTestDTO dto = runTest(model);
        assertFalse(dto.getSuccess(), "不存在的域名不应连通成功");
        assertTrue(dto.getErrMsg().startsWith("网络异常"), () -> "实际归类: " + dto.getErrMsg());
    }

    @Test
    @DisplayName("模型类型写错（非法值 0）：分发层单独抛 40000，不产生连通结果")
    void testIllegalModelType() {
        AModel model = bailianEmbedding();
        model.setModelType(0);
        BusinessException ex = assertThrows(BusinessException.class, () -> runTest(model));
        assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("模型类型写错（向量模型配成 Anthropic 格式）：注册层单独抛 45005")
    void testEmbeddingWithAnthropicFormat() {
        AModel model = bailianEmbedding();
        model.setApiFormat(AgentConstants.API_FORMAT_ANTHROPIC);
        BusinessException ex = assertThrows(BusinessException.class, () -> runTest(model));
        assertEquals(ErrorCode.API_FORMAT_OPENAI_CHAT.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("模型类型写错（配成 chat 但用向量模型 id）：chat 分支打向量模型，真实上游报错归类为模型不存在")
    void testChatTypeWithEmbeddingModelId() {
        AModel model = bailianEmbedding();
        model.setModelType(AgentConstants.MODEL_TYPE_CHAT);
        ModelTestDTO dto = runTest(model);
        assertFalse(dto.getSuccess(), "向量模型不应能走通 chat 接口");
        assertTrue(dto.getErrMsg().startsWith("模型不存在或路径错误"), () -> "实际归类: " + dto.getErrMsg());
    }

    @Test
    @DisplayName("api_model_id 写错（向量模型配成 chat 模型 qwen-plus）：embeddings 端点拒绝，归类为模型不存在")
    void testEmbeddingTypeWithChatModelId() {
        AModel model = bailianEmbedding();
        model.setApiModelId("qwen-plus");
        ModelTestDTO dto = runTest(model);
        assertFalse(dto.getSuccess(), "chat 模型不应能走通 embeddings 接口");
        assertTrue(dto.getErrMsg().startsWith("模型不存在或路径错误"), () -> "实际归类: " + dto.getErrMsg());
    }

    @Test
    @DisplayName("认证方式写错（向量模型配 Header 直传）：SDK 构建即抛 IllegalStateException（保存层已禁止该组合，锁定绕过保存层的边界）")
    void testNonBearerRejectedBySdk() {
        AModel model = bailianEmbedding();
        model.setAuthType(AgentConstants.AUTH_HEADER);
        model.setAuthKeyName("x-api-key");
        assertThrows(IllegalStateException.class, () -> runTest(model));
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

    /** 默认百炼向量模型配置（OpenAI 兼容端点 + Bearer），各用例按需覆盖字段 */
    private AModel bailianEmbedding() {
        AModel model = new AModel();
        model.setId(1L);
        model.setModelName("百炼向量模型");
        model.setModelType(AgentConstants.MODEL_TYPE_EMBEDDING);
        model.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        model.setApiFormat(AgentConstants.API_FORMAT_OPENAI_CHAT);
        model.setAuthType(AgentConstants.AUTH_BEARER);
        model.setApiModelId("qwen3.7-text-embedding");
        model.setApiKey(API_KEY);
        return model;
    }
}
