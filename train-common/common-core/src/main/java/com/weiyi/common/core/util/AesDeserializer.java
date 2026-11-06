package com.weiyi.common.core.util;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

/**
 * 请求入站解密（Jackson 3 / Boot 4 默认编解码）：标在 DTO 字段上，
 * 反序列化时还原 ENC: 密文为明文（前端提交前用 aes.js encrypt）。
 * 用法：{@code @JsonDeserialize(using = AesDeserializer.class)}（tools.jackson.databind.annotation）。
 */
public class AesDeserializer extends ValueDeserializer<String> {

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) {
        return AesUtil.decrypt(p.getString());
    }
}
