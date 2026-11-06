package com.weiyi.common.core.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 敏感字段（姓名/身份证/手机号）AES 加密工具，与前端 src/utils/aes.js 约定同一密钥。
 * 密文格式：ENC: + Base64(AES-128/ECB/PKCS5Padding)。
 * 加解密均幂等/容错：已加密原样返回、非密文/解密失败原样返回，防脏数据与双重加解密。
 * 两处用途：HTTP 传输层（DTO 注解）；库内身份证可逆加密（TypeHandler）。
 * 注意：前端 JS 内置同款密钥，传输层本质是防抓包嗅探而非机密保护（机密性靠 HTTPS）；
 * ECB 模式保证相同明文密文一致，是身份证等值查询所需。
 */
public final class AesUtil {

    /** 密文前缀标记：前后端都以该前缀识别密文 */
    public static final String PREFIX = "ENC:";

    /** 16 字符密钥 = AES-128 */
    private static volatile String key;

    private AesUtil() {
    }

    /** 启动时由配置注入密钥 */
    public static void init(String aesKey) {
        if (aesKey == null || aesKey.isBlank()) {
            throw new IllegalStateException("未配置敏感字段密钥 ntrain.crypto.aes-key，请通过环境变量 NTRAIN_AES_KEY 注入");
        }
        if (aesKey.length() != 16) {
            throw new IllegalStateException("敏感字段密钥 ntrain.crypto.aes-key 必须为 16 个字符（AES-128），当前长度 " + aesKey.length());
        }
        key = aesKey;
    }

    private static SecretKeySpec keySpec() {
        String current = key;
        if (current == null) {
            throw new IllegalStateException("敏感字段密钥未初始化，请通过环境变量 NTRAIN_AES_KEY 注入");
        }
        return new SecretKeySpec(current.getBytes(StandardCharsets.UTF_8), "AES");
    }

    /** 加密：输出 ENC: + Base64 密文；空值或已加密（ENC: 开头）的原样返回，保证幂等 */
    public static String encrypt(String plain) {
        if (plain == null || plain.isBlank() || plain.startsWith(PREFIX)) {
            return plain;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec());
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("AES加密失败", e);
        }
    }

    /** 解密：仅处理 ENC: 开头的密文，其他值（明文/空）与解密失败的值原样返回，容错防脏数据 */
    public static String decrypt(String value) {
        if (value == null || value.isBlank() || !value.startsWith(PREFIX)) {
            return value;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec());
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(value.substring(PREFIX.length())));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }
}
