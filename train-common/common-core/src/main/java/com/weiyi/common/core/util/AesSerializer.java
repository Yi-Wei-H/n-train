package com.weiyi.common.core.util;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

/**
 * 响应出站加密（Jackson 3 / Boot 4 默认编解码）：标在 DTO 字段上，
 * 序列化时输出 ENC: 密文（前端 axios 拦截器统一 deepDecrypt）。
 * 用法：{@code @JsonSerialize(using = AesSerializer.class)}（tools.jackson.databind.annotation）。
 */
public class AesSerializer extends ValueSerializer<String> {

    @Override
    public void serialize(String value, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeString(AesUtil.encrypt(value));
    }
}
