package com.weiyi.agent.server.controller;
import com.weiyi.agent.server.ai.provider.ModelListFetcher;

import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.api.dto.ModelItemDTO;
import com.weiyi.agent.api.dto.ModelListDTO;
import com.weiyi.agent.api.dto.ModelListReq;
import com.weiyi.agent.server.service.model.ModelListService;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.agent.server.controller.rpc.ModelController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 获取上游模型列表的真实测试：真实 HTTP 请求百炼 / DeepSeek（只读不计费）。
 * 走真实链路（ModelController → ModelListService → ModelListFetcher → 上游 /models 端点）。
 * 入参校验用例在校验层即抛出，无网络请求。
 */
class ModelListRemoteRealTest {

    /** 阿里百炼平台有效 key */
    private static final String BAILIAN_API_KEY = "RgBFxQf10rsrIb7RD5-LnC54";

    @Test
    @DisplayName("百炼真实拉取：keyword 过滤生效且按 id 字典序返回")
    void testBailianListSuccess() {
        ModelListReq req = bailianReq();
        req.setKeyword("embedding");
        ModelListDTO dto = runList(req);

        assertTrue(dto.getSuccess(), () -> "拉取失败: " + dto.getErrMsg());
        assertFalse(dto.getModels().isEmpty(), "keyword=embedding 不应过滤为空");
        for (ModelItemDTO m : dto.getModels()) {
            assertTrue(m.getId().toLowerCase().contains("embedding"), "未过滤干净: " + m.getId());
        }
        List<String> ids = dto.getModels().stream().map(ModelItemDTO::getId).toList();
        for (int i = 1; i < ids.size(); i++) {
            assertTrue(ids.get(i - 1).compareTo(ids.get(i)) <= 0, "未按字典序排序");
        }
    }

    @Test
    @DisplayName("无效 key：真实 401 归类为 API Key 无效")
    void testInvalidKey() {
        ModelListReq req = bailianReq();
        req.setApiKey("sk-invalid-key-for-list-test");
        ModelListDTO dto = runList(req);
        assertFalse(dto.getSuccess(), "无效 key 不应拉取成功");
        assertTrue(dto.getErrMsg().startsWith("API Key 无效或未授权"), () -> "实际归类: " + dto.getErrMsg());
    }

    @Test
    @DisplayName("base_url 错误：真实 DNS 失败归类为网络异常")
    void testBadBaseUrl() {
        ModelListReq req = bailianReq();
        req.setBaseUrl("https://dashscope.invalid");
        ModelListDTO dto = runList(req);
        assertFalse(dto.getSuccess(), "不存在的域名不应拉取成功");
        assertTrue(dto.getErrMsg().startsWith("网络异常"), () -> "实际归类: " + dto.getErrMsg());
    }

    @Test
    @DisplayName("Anthropic 格式 × Header 认证（x-api-key）：无效 key 401 立即终止，不再换候选路径")
    void testAnthropicHeaderAuth() {
        ModelListReq req = new ModelListReq();
        req.setBaseUrl("https://api.deepseek.com/anthropic");
        req.setApiKey("sk-invalid-key-for-list-test");
        req.setApiFormat(AgentConstants.API_FORMAT_ANTHROPIC);
        req.setAuthType(AgentConstants.AUTH_HEADER);
        req.setAuthKeyName("x-api-key");
        ModelListDTO dto = runList(req);
        assertFalse(dto.getSuccess(), "无效 key 不应拉取成功");
        assertTrue(dto.getErrMsg().startsWith("API Key 无效或未授权"), () -> "实际归类: " + dto.getErrMsg());
    }

    @Test
    @DisplayName("入参校验：OpenAI 格式 × 非 Bearer 认证拒绝（40000，无网络请求）")
    void testValidateOpenAiNonBearer() {
        ModelListReq req = bailianReq();
        req.setAuthType(AgentConstants.AUTH_HEADER);
        req.setAuthKeyName("x-api-key");
        BusinessException ex = assertThrows(BusinessException.class, () -> runList(req));
        assertEquals(40000, ex.getCode());
        assertEquals("OpenAI 兼容格式拉去模型仅支持 Bearer 认证", ex.getMessage());
    }

    @Test
    @DisplayName("入参校验：apiFormat=2 无客户端实现拒绝（40000，无网络请求）")
    void testValidateUnsupportedFormat() {
        ModelListReq req = bailianReq();
        req.setApiFormat(AgentConstants.API_FORMAT_OPENAI_RESPONSES);
        BusinessException ex = assertThrows(BusinessException.class, () -> runList(req));
        assertEquals(40000, ex.getCode());
    }

    private ModelListDTO runList(ModelListReq req) {
        ModelListDTO result = new ModelController(null, null, new ModelListService(null)).listRemote(req);
        System.out.println("拉取结果: success=" + result.getSuccess()
                + ", count=" + (result.getModels() == null ? 0 : result.getModels().size())
                + ", errMsg=" + result.getErrMsg());
        return result;
    }

    /** 默认百炼 OpenAI 兼容配置（Bearer），各用例按需覆盖字段 */
    private ModelListReq bailianReq() {
        ModelListReq req = new ModelListReq();
        req.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        req.setApiKey(BAILIAN_API_KEY);
        req.setApiFormat(AgentConstants.API_FORMAT_OPENAI_CHAT);
        req.setAuthType(AgentConstants.AUTH_BEARER);
        return req;
    }
}
