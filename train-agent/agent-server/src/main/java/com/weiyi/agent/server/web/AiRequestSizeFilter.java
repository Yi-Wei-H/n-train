package com.weiyi.agent.server.web;

import com.weiyi.agent.server.config.AgentProperties;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.result.Result;
import com.weiyi.common.core.util.JacksonUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * AI 接口接入过滤：请求体大小与 content-type 前置校验（在参数解析、限流与业务处理之前）。
 * 过滤器先于 DispatcherServlet 执行，异常不经过全局异常处理器，拒绝时直接写出 JSON 响应。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiRequestSizeFilter extends OncePerRequestFilter {

    private static final String AI_PATH_PREFIX = "/ai/";

    private final AgentProperties agentProperties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 仅校验 AI 接口下携带请求体的方法；GET/HEAD 无请求体，跳过
        String method = request.getMethod();
        boolean bodyMethod = "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
        return !bodyMethod || !request.getRequestURI().startsWith(AI_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long length = request.getContentLengthLong();
        if (length < 0) {
            // 无 Content-Length（如 chunked 传输）无法前置判定大小：必有请求体的方法统一拒绝防大包绕过；
            // DELETE 通常无请求体，放行（Spring 侧不解 body）
            if (!"DELETE".equalsIgnoreCase(request.getMethod())) {
                reject(response, Result.fail(ErrorCode.AI_INPUT_INVALID, "请求必须携带明确的 Content-Length"));
                return;
            }
        } else if (length > agentProperties.getSecurity().getMaxRequestBodyBytes()) {
            reject(response, Result.fail(ErrorCode.AI_INPUT_INVALID, "请求体过大"));
            return;
        }
        if (length > 0) {
            String contentType = request.getContentType();
            if (contentType == null || !contentType.contains(MediaType.APPLICATION_JSON_VALUE)) {
                reject(response, Result.fail(ErrorCode.AI_INPUT_INVALID, "仅支持 JSON 请求"));
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, Result<Void> body) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(JacksonUtil.toJson(body));
    }
}
