package com.weiyi.common.core.util;

/**
 * IP 规范化：统一 Java 双栈网络下同一地址的多种上报形式，避免日志口径不一与字符串精确匹配失配。
 */
public final class IpUtil {

    private IpUtil() {
    }

    /**
     * IPv4-mapped IPv6（::ffff:1.2.3.4，双栈 socket 上报 IPv4 连接的常见形式）还原为 IPv4；
     * IPv6 环回（::1 及其全零展开形式）归一为 127.0.0.1；其余原样返回。
     */
    public static String normalize(String ip) {
        if (ip == null || ip.isBlank()) {
            return ip;
        }
        String v = ip.trim();
        if (v.startsWith("::ffff:") && v.indexOf('.', 7) > 0) {
            return v.substring(7);
        }
        if ("::1".equals(v) || "0:0:0:0:0:0:0:1".equals(v)) {
            return "127.0.0.1";
        }
        return v;
    }
}
