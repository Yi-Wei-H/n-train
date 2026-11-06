package com.weiyi.common.core.util;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jackson 3 静态封装（内部使用：日志打印、HTTP 无关场景）。
 * J3 默认即：日期写 ISO 字符串、忽略未知属性、java.time 内置支持，无需显式配置。
 * 注意：Redisson codec / Redis 缓存序列化用 common-redis 的 CodecMappers（Jackson 2），
 * 与本类互不相干（Redisson codec 系 fasterxml API，双库共存）。
 */
@Slf4j
public final class JacksonUtil {

    public static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private JacksonUtil() {
    }

    public static String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("toJson failed: {}", e.getMessage());
            return null;
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            log.warn("fromJson failed: {}", e.getMessage());
            return null;
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            log.warn("fromJson failed: {}", e.getMessage());
            return null;
        }
    }
}
