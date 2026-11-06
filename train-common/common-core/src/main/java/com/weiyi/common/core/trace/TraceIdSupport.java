package com.weiyi.common.core.trace;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 全链路 traceId 载体契约的统一实现：生成格式、合法性判定、MDC 读写。
 *
 * <p>只依赖 slf4j（各模块都拿得到），因此 common-core 不含任何 Web / MQ / Feign 依赖，
 * 网关（WebFlux）、服务（Servlet Filter）、Feign 拦截器、MQ 收发、Quartz Job 都复用它，
 * 保证头名、长度、字符集、MDC key 四处永远一致。
 *
 * <p>故障隔离：本类任何方法都不外抛异常——trace 标记失败只 warn，业务按无标记继续。
 */
@Slf4j
public final class TraceIdSupport {

    /** MDC key，与各服务 logback pattern 的 %X{traceId} 对应 */
    public static final String MDC_TRACE_ID = "traceId";

    /** traceId 固定长度（位） */
    public static final int LENGTH = 16;

    /** 合法 traceId：恰好 {@link #LENGTH} 位小写十六进制 */
    private static final Pattern FORMAT = Pattern.compile("^[0-9a-f]{" + LENGTH + "}$");

    private TraceIdSupport() {
    }

    /**
     * 生成 traceId：{@link #LENGTH} 位小写十六进制（UUID 去连字符取前 16 位）。
     * 失败不抛异常，返回 null 表示本次无标记可用。
     */
    public static String generate() {
        try {
            return UUID.randomUUID().toString().replace("-", "").substring(0, LENGTH);
        } catch (Exception e) {
            warn("生成失败", e);
            return null;
        }
    }

    /**
     * 合法值：非 null、恰好 {@link #LENGTH} 位、全为小写十六进制字符。
     * 不满足即视为非法（空白、长度不等、大写、含非 hex 字符、业务单号等），一律丢弃重生成——
     * 保证全链路 traceId 长度与字符集一致，也防止上游用固定值冒充 traceId 造成日志串流。
     */
    public static boolean isValid(String traceId) {
        return traceId != null && FORMAT.matcher(traceId).matches();
    }

    /** 取合法上游值，非法/缺失则生成新的（下游一律不会看到非法原值） */
    public static String resolve(String upstreamTraceId) {
        return isValid(upstreamTraceId) ? upstreamTraceId : generate();
    }

    /** 从 MDC 取当前 traceId，无则返回 null */
    public static String current() {
        try {
            return MDC.get(MDC_TRACE_ID);
        } catch (Exception e) {
            warn("读取 MDC 失败", e);
            return null;
        }
    }

    /** 写 MDC */
    public static void bind(String traceId) {
        if (traceId == null) {
            return;
        }
        try {
            MDC.put(MDC_TRACE_ID, traceId);
        } catch (Exception e) {
            warn("写入 MDC 失败", e);
        }
    }

    /** 清理 MDC：所有 put 都必须有配对的 remove（finally 中调用），防池化线程脏串 */
    public static void clear() {
        try {
            MDC.remove(MDC_TRACE_ID);
        } catch (Exception e) {
            warn("清理 MDC 失败", e);
        }
    }

    private static void warn(String action, Exception e) {
        try {
            log.warn("[trace] traceId {}，本次按无标记继续: {}", action, e.getMessage());
        } catch (Exception ignored) {
            // 日志组件本身失败时不再做任何事，绝不外抛
        }
    }
}
