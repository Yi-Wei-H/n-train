package com.weiyi.common.web.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Web 层 Jackson 3（Boot 4 默认编解码）全局配置：
 * Long/BigInteger 序列化为字符串（防前端 JS 精度丢失雪花 ID）；
 * 时间统一 "yyyy-MM-dd HH:mm:ss"。Boot 4 自动收集 SimpleModule bean 注入默认 JsonMapper。
 */
@AutoConfiguration
public class JacksonWebConfig {

    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String TIME_FORMAT = "HH:mm:ss";

    @Bean
    public SimpleModule ntrainWebJacksonModule() {
        DateTimeFormatter dateTime = DateTimeFormatter.ofPattern(DATE_TIME_FORMAT);
        DateTimeFormatter date = DateTimeFormatter.ofPattern(DATE_FORMAT);
        DateTimeFormatter time = DateTimeFormatter.ofPattern(TIME_FORMAT);

        SimpleModule module = new SimpleModule("ntrainWebJackson");
        module.addSerializer(Long.class, new ToStringJsonSerializer());
        module.addSerializer(Long.TYPE, new ToStringJsonSerializer());
        module.addSerializer(BigInteger.class, new ToStringJsonSerializer());
        module.addSerializer(LocalDateTime.class, new ValueSerializer<LocalDateTime>() {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator gen, SerializationContext ctxt) {
                gen.writeString(dateTime.format(value));
            }
        });
        module.addSerializer(LocalDate.class, new ValueSerializer<LocalDate>() {
            @Override
            public void serialize(LocalDate value, JsonGenerator gen, SerializationContext ctxt) {
                gen.writeString(date.format(value));
            }
        });
        module.addSerializer(LocalTime.class, new ValueSerializer<LocalTime>() {
            @Override
            public void serialize(LocalTime value, JsonGenerator gen, SerializationContext ctxt) {
                gen.writeString(time.format(value));
            }
        });
        module.addDeserializer(LocalDateTime.class, new ValueDeserializer<LocalDateTime>() {
            @Override
            public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) {
                return LocalDateTime.parse(p.getString(), dateTime);
            }
        });
        module.addDeserializer(LocalDate.class, new ValueDeserializer<LocalDate>() {
            @Override
            public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) {
                return LocalDate.parse(p.getString(), date);
            }
        });
        module.addDeserializer(LocalTime.class, new ValueDeserializer<LocalTime>() {
            @Override
            public LocalTime deserialize(JsonParser p, DeserializationContext ctxt) {
                return LocalTime.parse(p.getString(), time);
            }
        });
        return module;
    }

    private static class ToStringJsonSerializer extends ValueSerializer<Object> {
        @Override
        public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) {
            gen.writeString(String.valueOf(value));
        }
    }
}
