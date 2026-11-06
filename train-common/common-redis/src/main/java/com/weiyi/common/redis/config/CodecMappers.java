package com.weiyi.common.redis.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Redisson JSON codec 专用 Jackson 2 mapper（fasterxml）。
 * Redisson 4.0 的 TypedJsonJacksonCodec 系 fasterxml API（尚无 Jackson 3 codec，4.5 起才有），
 * 与 HTTP 层的 Jackson 3（tools.jackson）双库共存、互不相干；
 * 配置与既有缓存写入时的 mapper 保持一致，升级前后缓存字节兼容。
 */
public final class CodecMappers {

    public static final ObjectMapper CODEC = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private CodecMappers() {
    }
}
