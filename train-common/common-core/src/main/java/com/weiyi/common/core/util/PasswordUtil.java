package com.weiyi.common.core.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;

/**
 * BCrypt 加解密 + 随机强密码生成。
 */
public final class PasswordUtil {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGIT = "23456789";
    private static final String SPECIAL = "!@#$%^&*";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordUtil() {
    }

    public static String hash(String raw) {
        return ENCODER.encode(raw);
    }

    public static boolean matches(String raw, String hashed) {
        return ENCODER.matches(raw, hashed);
    }

    /** 生成 length 位混合强密码（必含大小写/数字/特殊字符各一） */
    public static String randomStrong(int length) {
        if (length < 8) {
            length = 8;
        }
        StringBuilder sb = new StringBuilder(length);
        sb.append(pick(UPPER)).append(pick(LOWER)).append(pick(DIGIT)).append(pick(SPECIAL));
        String all = UPPER + LOWER + DIGIT + SPECIAL;
        for (int i = sb.length(); i < length; i++) {
            sb.append(pick(all));
        }
        // 打乱顺序
        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = chars[i];
            chars[i] = chars[j];
            chars[j] = tmp;
        }
        return new String(chars);
    }

    private static char pick(String source) {
        return source.charAt(RANDOM.nextInt(source.length()));
    }
}
