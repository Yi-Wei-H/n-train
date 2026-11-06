package com.weiyi.agent.server.service.model;

import com.weiyi.agent.server.ai.provider.CorrectBaseURL;
import com.weiyi.agent.server.ai.provider.ModelListFetcher;
import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.api.dto.ModelListDTO;
import com.weiyi.agent.api.dto.ModelListReq;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.util.BizAssert;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 获取上游模型列表（仅文本模型表单使用）：入参校验后交 ModelListFetcher 实时拉取，不落库不缓存。
 * 校验规则与模型保存一致：base_url 归一、api_key 长度、格式与认证矩阵、authKeyName 条件必填。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelListService {

    private final AModelService aModelService;

    public ModelListDTO listRemoteModels(ModelListReq req) {
        // 编辑场景：apiKey 留空但携带 modelId → 用库存 key（TypeHandler 已解密），改库换 key 后无需在前端重输
        if ((req.getApiKey() == null || req.getApiKey().isBlank()) && req.getModelId() != null) {
            req.setApiKey(aModelService.getModelById(req.getModelId()).getApiKey());
        }
        normalizeAndValidate(req);
        log.info("拉取上游模型列表: baseUrl={}, apiFormat={}, authType={}",
                req.getBaseUrl(), req.getApiFormat(), req.getAuthType());
        return ModelListFetcher.fetch(req);
    }

    private void normalizeAndValidate(ModelListReq req) {
        String baseUrl = CorrectBaseURL.stripTrailingSlashes(req.getBaseUrl());
        CorrectBaseURL.verifyStartHttp(baseUrl);
        req.setBaseUrl(baseUrl);

        BizAssert.isTrue(req.getApiKey() != null && req.getApiKey().length() >= 16 && req.getApiKey().length() <= 200,
                ErrorCode.PARAM_ERROR, "api_key 长度须在 16~200");
        BizAssert.isTrue(AgentConstants.API_FORMAT_OPENAI_CHAT == req.getApiFormat()
                        || AgentConstants.API_FORMAT_ANTHROPIC == req.getApiFormat(),
                ErrorCode.PARAM_ERROR, "目前仅支持 OpenAI 兼容与 Anthropic 格式");
        if (AgentConstants.API_FORMAT_OPENAI_CHAT == req.getApiFormat()) {
            BizAssert.isTrue(AgentConstants.AUTH_BEARER == req.getAuthType(),
                    ErrorCode.PARAM_ERROR, "OpenAI 兼容格式拉去模型仅支持 Bearer 认证");
        } else {
            BizAssert.isTrue(req.getAuthType() != null && req.getAuthType() >= AgentConstants.AUTH_BEARER
                            && req.getAuthType() <= AgentConstants.AUTH_QUERY,
                    ErrorCode.PARAM_ERROR, "认证方式错误");
        }
        if (AgentConstants.AUTH_BEARER == req.getAuthType()) {
            req.setAuthKeyName(null);
        } else {
            BizAssert.notBlank(req.getAuthKeyName(), ErrorCode.PARAM_ERROR, "该认证方式必须填写认证字段名 authKeyName");
        }
    }
}
