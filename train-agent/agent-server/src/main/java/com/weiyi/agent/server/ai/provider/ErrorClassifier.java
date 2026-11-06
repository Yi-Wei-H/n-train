package com.weiyi.agent.server.ai.provider;

import com.weiyi.common.core.exception.BusinessException;

import java.io.IOException;

/**
 * 上游调用失败原因归类：网络异常 / key 无效 / 余额不足 / 权限受限 / 模型或地址错误 / 限流 / 上游 5xx，兜底"连接失败"。
 * 连通性测试与模型列表获取共用。
 * openai-java 与 anthropic-java 两套 SDK 的异常类名不同且可能被包装（如 openai-java 把传输层
 * IOException 包成 OpenAIIoException("Request failed")），故沿 cause 链检测 IOException 并把
 * 链上「异常类名 + message」全部纳入关键词匹配，不依赖具体 SDK 类型，两类上游通用。
 */
public final class ErrorClassifier {

    /** 无法归类时的兜底归类文案前缀：chat 场景下统一转成"系统繁忙"，技术细节只留日志 */
    private static final String CONNECTION_FAILED_PREFIX = "连接失败";

    /** 用户向兜底文案：无法归类的上游/系统异常统一提示，技术细节只留在日志 */
    private static final String BUSY_MESSAGE = "系统繁忙，请稍后重试";

    private ErrorClassifier() {
    }

    public static String classify(Throwable e) {
        String name = e.getClass().getSimpleName();
        String msg = e.getMessage() == null ? "" : e.getMessage();
        StringBuilder chain = new StringBuilder(name).append(' ').append(msg).append(' ');
        boolean networkLayer = e instanceof IOException;
        for (Throwable t = e.getCause(); t != null; t = t.getCause()) {
            networkLayer = networkLayer || t instanceof IOException;
            chain.append(t.getClass().getSimpleName()).append(' ')
                    .append(t.getMessage() == null ? "" : t.getMessage()).append(' ');
        }
        String text = chain.toString().toLowerCase();

        // 网络层放最前：超时/拒绝连接/DNS 失败（部分报文带 408/504 字样，先于 5xx 判掉）
        if (networkLayer || containsAny(text, "timeout", "timed out", "unknownhost",
                "connection refused", "connectexception", "socketexception", "network")) {
            return "网络异常：无法连接上游服务，请检查 base_url 与网络连通性";
        }
        // key 不对：OpenAI 系 401/Unauthorized，Anthropic 系 AuthenticationError，报文带 invalid api key 等
        if (containsAny(text, "401", "unauthorized", "authentication", "invalid api key",
                "invalid_api_key", "incorrect api key")) {
            return "API Key 无效或未授权，请检查 api_key 与认证方式配置";
        }
        // 余额不足：402 / 厂商欠费文案；OpenAI 欠费报 429 + insufficient_quota，也归这里（先于限流判）
        if (containsAny(text, "402", "insufficient", "quota", "balance", "arrearage", "余额")) {
            return "余额不足或配额耗尽，请检查上游账户余额";
        }
        if (containsAny(text, "403", "forbidden", "permissiondenied")) {
            return "无权访问（403），请检查 Key 的权限范围";
        }
        // DeepSeek 对未知模型报 400 且文案为 "...but you passed xxx"（不走 404），一并归到这里
        if (containsAny(text, "404", "notfound", "not found", "model_not_found", "does not exist", "but you passed")) {
            return "模型不存在或路径错误，请检查 api_model_id 与 base_url";
        }
        if (containsAny(text, "429", "ratelimit", "rate limit", "too many requests")) {
            return "请求被限流（429），请稍后重试";
        }
        if (containsAny(text, "500", "502", "503", "504", "internalserver", "badgateway", "serviceunavailable")) {
            return "上游服务异常（5xx），请稍后重试";
        }
        return CONNECTION_FAILED_PREFIX + "：" + (msg.isBlank() ? name : msg.substring(0, Math.min(msg.length(), 200)));
    }

    private static boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 面向用户的错误文案（聊天失败轮落库与 error 事件共用口径）：
     * 业务异常（授权关闭/超配额等人为约定）用其 message 本身；
     * 可归类的上游厂家异常（Key 无效/模型不存在/限流/5xx 等）用归类文案；
     * 其余（含"连接失败"兜底与未知异常）统一"系统繁忙，请稍后重试"。
     */
    public static String userFacing(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof BusinessException businessException && businessException.getMessage() != null
                    && !businessException.getMessage().isBlank()) {
                return businessException.getMessage();
            }
        }
        String classified = classify(e);
        return classified.startsWith(CONNECTION_FAILED_PREFIX) ? BUSY_MESSAGE : classified;
    }
}
