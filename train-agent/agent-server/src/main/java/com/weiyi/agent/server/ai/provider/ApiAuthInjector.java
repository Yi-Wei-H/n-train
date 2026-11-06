package com.weiyi.agent.server.ai.provider;

import com.anthropic.core.ClientOptions;
import com.openai.credential.BearerTokenCredential;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.server.entity.AModel;

/**
 * 模型认证注入：按 a_model 的认证两维（auth_type + auth_key_name）把 API KEY 装进官方 SDK 的
 * ClientOptions，与 api_format 正交——两族 builder 都提供 putHeader/putQueryParam：
 * <ul>
 *   <li>auth_type=1 Bearer 头：OpenAI 系走 SDK 原生 apiKey()（内部即 Authorization: Bearer）；
 *       Anthropic 系 core 层无 apiKey() 一等方法，手动放 Authorization 头；</li>
 *   <li>auth_type=2 Key 头直传：putHeader(auth_key_name, key)，如 x-api-key；</li>
 *   <li>auth_type=3 查询参数：putQueryParam(auth_key_name, key)。</li>
 * </ul>
 */
public final class ApiAuthInjector {

    private ApiAuthInjector() {
    }

    /** OpenAI 系（openai-java core） */
    public static void apply(com.openai.core.ClientOptions.Builder builder, AModel model) {
        String key = model.getApiKey();
        switch (model.getAuthType()) {
            case AgentConstants.AUTH_BEARER -> {
                builder.apiKey(key);
                builder.credential(BearerTokenCredential.create(key));
            }
            case AgentConstants.AUTH_HEADER -> builder.putHeader(requiredKeyName(model), key);
            case AgentConstants.AUTH_QUERY -> builder.putQueryParam(requiredKeyName(model), key);
            default -> throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "未知认证方式: " + model.getAuthType() + ", modelId=" + model.getId());
        }
    }

    /** Anthropic 系（anthropic-java core：无 apiKey() 一等方法，全走头/参数注入） */
    public static void apply(ClientOptions.Builder builder, AModel model) {
        String key = model.getApiKey();
        switch (model.getAuthType()) {
            case AgentConstants.AUTH_BEARER -> builder.putHeader("Authorization", "Bearer " + key);
            case AgentConstants.AUTH_HEADER -> builder.putHeader(requiredKeyName(model), key);
            case AgentConstants.AUTH_QUERY -> builder.putQueryParam(requiredKeyName(model), key);
            default -> throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "未知认证方式: " + model.getAuthType() + ", modelId=" + model.getId());
        }
    }

    /** auth_type=2/3 必须有认证字段名（保存校验已保证新数据，此处防历史数据/手工改库） */
    private static String requiredKeyName(AModel model) {
        String name = model.getAuthKeyName();
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "该认证方式必须填写认证字段名 authKeyName, modelId=" + model.getId());
        }
        return name.trim();
    }
}
