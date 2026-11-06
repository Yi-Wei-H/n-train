package com.weiyi.agent.server.ai.provider;

import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.agent.server.constant.AgentConstants;

import java.util.regex.Pattern;

/**
 * 用于修正各个上游类型的baseURL
 * <p>矫正URL
 */
public class CorrectBaseURL {

    /** URL 末尾的版本段：/v1、/v2、/v4beta 等 */
    private static final Pattern TRAILING_VERSION = Pattern.compile("/v\\d+[a-zA-Z0-9]*$");

    public static void verifyStartHttp(String value) {
        BizAssert.isTrue(value.startsWith("http://") || value.startsWith("https://"),
                ErrorCode.PARAM_ERROR, "base_url 必须以 http:// 或 https:// 开头");
    }

    /**
     * 按上游格式修正 base_url（构建连接实例时调用）：trim + 去尾斜杠 + 版本段对齐。
     * <p>两族官方 SDK 的拼接约定相反：
     * OpenAI 系 base 必须含版本段，无则补 /v1；
     * Anthropic 系（anthropic-java SDK 自拼 /v1/messages）base 不得含版本段，有则去掉（防 /v1/v1 404）；
     * format 2/4 暂无客户端实现，仅做基础清理后原样返回。
     *
     * @param apiFormat 上游格式
     * @param baseUrl   原始 base_url
     * @return 修正后的 base_url
     */
    public static String correctBaseUrl(Integer apiFormat, String baseUrl) {
        String stripped = stripTrailingSlashes(baseUrl);
        boolean versioned = TRAILING_VERSION.matcher(stripped).find();
        if (AgentConstants.API_FORMAT_ANTHROPIC == apiFormat) {
            return versioned ? stripped.substring(0, stripped.lastIndexOf('/')) : stripped;
        }
        if (AgentConstants.API_FORMAT_OPENAI_CHAT == apiFormat) {
            return versioned ? stripped : stripped + "/v1";
        }
        return stripped;
    }

    /*去除尾部‘/’*/
    public static String stripTrailingSlashes(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /** 是否以版本段（/v1、/v4、/v4beta 等）结尾——模型列表候选 URL 生成复用同一判定，保持与归一规则同源 */
    public static boolean endsWithVersionSegment(String url) {
        return TRAILING_VERSION.matcher(stripTrailingSlashes(url)).find();
    }
}
