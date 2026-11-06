package com.weiyi.business.api.config;

import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.result.Result;
import com.weiyi.common.core.util.JacksonUtil;
import feign.Response;
import feign.codec.ErrorDecoder;

import java.io.InputStream;

/**
 * Feign 错误解码：ticket 服务返回 4xx/5xx + Result JSON 时，
 * 还原成携带原始错误码的 BusinessException（而非笼统的 FeignException）。
 */
public class BusinessApiErrorDecoder implements ErrorDecoder {

    @Override
    public Exception decode(String methodKey, Response response) {
        try (InputStream body = response.body().asInputStream()) {
            String json = new String(body.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            Result<?> result = JacksonUtil.fromJson(json, Result.class);
            if (result != null && result.getCode() != 0) {
                return new BusinessException(result.getCode(), result.getMessage());
            }
        } catch (Exception ignore) {
            // 落到下方兜底
        }
        return new BusinessException(ErrorCode.REMOTE_ERROR,
                "ticket 服务调用失败：" + methodKey + " HTTP " + response.status());
    }
}
