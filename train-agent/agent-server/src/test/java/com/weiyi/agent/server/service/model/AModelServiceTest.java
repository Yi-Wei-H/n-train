package com.weiyi.agent.server.service.model;
import com.weiyi.agent.server.service.model.AModelService;

import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.api.dto.AModelReq;
import com.weiyi.agent.server.entity.AModel;
import com.weiyi.agent.server.mapper.AModelMapper;
import com.weiyi.common.core.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 保存校验规则测试：重点覆盖「OpenAI 兼容格式仅支持 Bearer 认证」——
 * openai-java SDK 强制标准 credential，Key 头/查询参数认证无法构建 OpenAI 系客户端。
 * AModelMapper 用 Mockito 替身，不依赖数据库。
 */
class AModelServiceTest {

    private AModelMapper aModelMapper;
    private AModelService modelService;

    @BeforeEach
    void setUp() {
        aModelMapper = mock(AModelMapper.class);
        when(aModelMapper.insert(org.mockito.ArgumentMatchers.any(AModel.class))).thenReturn(1);
        modelService = new AModelService(aModelMapper, mock(ApplicationEventPublisher.class));
    }

    @Test
    @DisplayName("OpenAI 格式 × Header/Query 认证：保存被拒（40000），不入库")
    void testOpenAiNonBearerRejected() {
        for (int authType : List.of(AgentConstants.AUTH_HEADER, AgentConstants.AUTH_QUERY)) {
            AModelReq req = baseReq(AgentConstants.API_FORMAT_OPENAI_CHAT, authType);
            req.setAuthKeyName(authType == AgentConstants.AUTH_HEADER ? "x-api-key" : "api_key");
            BusinessException ex = assertThrows(BusinessException.class, () -> modelService.createOrUpdateModel(req));
            assertEquals(40000, ex.getCode());
            assertEquals("OpenAI 兼容格式仅支持 Bearer 认证", ex.getMessage());
        }
        verify(aModelMapper, never()).insert(org.mockito.ArgumentMatchers.any(AModel.class));
    }

    @Test
    @DisplayName("OpenAI 格式 × Bearer：可保存，且 authKeyName 被强制清空")
    void testOpenAiBearerSaved() {
        AModelReq req = baseReq(AgentConstants.API_FORMAT_OPENAI_CHAT, AgentConstants.AUTH_BEARER);
        req.setAuthKeyName("x-api-key");

        modelService.createOrUpdateModel(req);

        ArgumentCaptor<AModel> captor = ArgumentCaptor.forClass(AModel.class);
        verify(aModelMapper).insert(captor.capture());
        assertNull(captor.getValue().getAuthKeyName(), "Bearer 认证下 authKeyName 应被强制清空");
        assertEquals(AgentConstants.STATUS_OFF, captor.getValue().getStatus(), "新增模型应固定下架");
    }

    @Test
    @DisplayName("Anthropic 格式 × Header 认证：正交仍成立，可保存")
    void testAnthropicHeaderSaved() {
        AModelReq req = baseReq(AgentConstants.API_FORMAT_ANTHROPIC, AgentConstants.AUTH_HEADER);
        req.setAuthKeyName("x-api-key");

        modelService.createOrUpdateModel(req);

        ArgumentCaptor<AModel> captor = ArgumentCaptor.forClass(AModel.class);
        verify(aModelMapper).insert(captor.capture());
        assertEquals("x-api-key", captor.getValue().getAuthKeyName());
    }

    private AModelReq baseReq(int apiFormat, int authType) {
        AModelReq req = new AModelReq();
        req.setModelName("test-model");
        req.setModelType(AgentConstants.MODEL_TYPE_CHAT);
        req.setBaseUrl("https://api.test.com");
        req.setApiFormat(apiFormat);
        req.setAuthType(authType);
        req.setApiModelId("test-model-id");
        req.setApiKey("sk-test-key-1234567890");
        return req;
    }
}
