package com.weiyi.common.redis.util;

import com.weiyi.common.core.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 请求身份工具：限流维度取值共用（IP / 设备指纹）。
 */
public final class RequestIdentityUtil {

    /** 前端设备指纹头（FingerprintJS 生成，localStorage 持久，随请求携带） */
    public static final String HEADER_DEVICE_ID = "X-Device-Id";

    private RequestIdentityUtil() {
    }

    /** 网关注入的可信客户端 IP 头（网关已剥离伪造的网络转发头后计算） */
    public static final String HEADER_CLIENT_IP = "X-Client-IP";

    /** 客户端 IP：优先网关注入的可信 X-Client-IP，其次 X-Forwarded-For 首段，最后回退连接地址（统一规范化）；无请求上下文返回 no-request（全部归入同一兜底窗口） */
    public static String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            String clientIp = request.getHeader(HEADER_CLIENT_IP);
            if (StringUtils.hasText(clientIp)) {
                return IpUtil.normalize(clientIp);
            }
            String forwarded = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                return IpUtil.normalize(forwarded.split(",")[0]);
            }
            return IpUtil.normalize(request.getRemoteAddr());
        }
        return "no-request";
    }

    /** 设备指纹：未携带返回 null，由调用方决定回退策略（限流切面回退 IP） */
    public static String currentDeviceId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            String deviceId = attrs.getRequest().getHeader(HEADER_DEVICE_ID);
            return StringUtils.hasText(deviceId) ? deviceId.trim() : null;
        }
        return null;
    }
}
