package com.weiyi.common.core.util;

/**
 * 脱敏工具：手机号、证件号、姓名。
 */
public final class DesensitizeUtil {

    private DesensitizeUtil() {
    }

    /** 手机号：138****8000 */
    public static String phone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
    }

    /** 身份证：保留前 3 后 4 */
    public static String idCard(String idCard) {
        if (idCard == null || idCard.length() < 8) {
            return idCard;
        }
        return idCard.substring(0, 3) + "***********" + idCard.substring(idCard.length() - 4);
    }

    /** 姓名：张*丰 */
    public static String name(String name) {
        if (name == null || name.length() <= 1) {
            return name;
        }
        if (name.length() == 2) {
            return name.charAt(0) + "*";
        }
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }
}
