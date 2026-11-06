package com.weiyi.gateway.filter;

import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.result.Result;
import com.weiyi.common.core.util.IpUtil;
import com.weiyi.common.core.util.JacksonUtil;
import com.weiyi.gateway.config.AuthProperties;
import com.weiyi.gateway.dto.TokenUser;
import com.weiyi.gateway.util.TokenVerifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 全局 token 校验：
 * 1. 无论是否白名单，先剥离请求自带的 X-User-* 伪造头与伪造的网络身份头（转发/IP/设备）；
 * 2. 白名单放行（登录/注册/验证码/文档...）；
 * 3. 其余必须携带有效 Bearer token，校验通过后向下游注入登录态头；
 * 4. 统一计算并注入可信 X-Client-IP，规范化 X-Device-Id——下游限流只用网关注入值，不读公网原始转发头。
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_TYPE = "X-User-Type";
    private static final String HEADER_USERNAME = "X-Username";
    private static final String HEADER_USER_SUPER = "X-User-Super";
    private static final String HEADER_CLIENT_IP = "X-Client-IP";
    private static final String HEADER_DEVICE_ID = "X-Device-Id";
    private static final String HEADER_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_REAL_IP = "X-Real-IP";
    private static final String BEARER_PREFIX = "Bearer ";

    /** 设备标识规范化：只保留字母数字与-_，超长截断 */
    private static final int DEVICE_ID_MAX_LEN = 64;

    private final TokenVerifier tokenVerifier;
    private final List<String> whitelist;
    private final List<String> trustedProxies;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AuthGlobalFilter(TokenVerifier tokenVerifier, AuthProperties authProperties) {
        this.tokenVerifier = tokenVerifier;
        this.whitelist = authProperties.getWhitelist();
        // 与连接地址同步规范化，保证 IPv4-mapped/环回等变体写法也能精确匹配
        this.trustedProxies = authProperties.getTrustedProxies().stream().map(IpUtil::normalize).toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        /* request是不可变的。创建请求体构建器，以便后续更改请求体*/
        ServerHttpRequest.Builder mutated = request.mutate();
        // 客户端 IP：仅当直连对端是可信反代时采信其追加的 XFF 末段（append 语义下末段才是反代直连观测地址）；
        // 其余一律按连接地址，直连网关的伪造 XFF 完全无效
        String clientIp = resolveClientIp(request);
        String deviceId = normalizeDeviceId(request.getHeaders().getFirst(HEADER_DEVICE_ID));
        mutated.headers(headers -> {
            headers.remove(HEADER_USER_ID);
            headers.remove(HEADER_USER_TYPE);
            headers.remove(HEADER_USERNAME);
            headers.remove(HEADER_USER_SUPER);
            headers.remove(HEADER_FORWARDED_FOR);
            headers.remove(HEADER_REAL_IP);
            headers.remove(HEADER_DEVICE_ID);
            headers.set(HEADER_CLIENT_IP, clientIp);
            if (deviceId != null) {
                headers.set(HEADER_DEVICE_ID, deviceId);
            }
        });

        if (isWhitelisted(path)) {
            return chain.filter(exchange.mutate().request(mutated.build()).build());
        }

        String authorization = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return reject(exchange, Result.fail(ErrorCode.UNAUTHORIZED));
        }
        try {
            TokenUser user = tokenVerifier.verify(authorization.substring(BEARER_PREFIX.length()));
            mutated
                    .header(HEADER_USER_ID, String.valueOf(user.userId()))
                    .header(HEADER_USER_TYPE, user.userType() == null ? "" : user.userType())
                    .header(HEADER_USERNAME, user.username() == null ? "" : user.username());
            if (user.superAdmin()) {
                mutated.header(HEADER_USER_SUPER, "1");
            }
            return chain.filter(exchange.mutate().request(mutated.build()).build());
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            return reject(exchange, Result.fail(ErrorCode.TOKEN_EXPIRED));
        } catch (Exception e) {
            log.debug("token verify failed: {}", e.getMessage());
            return reject(exchange, Result.fail(ErrorCode.UNAUTHORIZED));
        }
    }

    /** 客户端 IP：直连对端是可信反代时取其追加的 XFF 末段，否则按连接地址（伪造转发头无效）；结果统一规范化 */
    private String resolveClientIp(ServerHttpRequest request) {
        InetSocketAddress remote = request.getRemoteAddress();
        String remoteAddr = IpUtil.normalize(remote != null ? remote.getAddress().getHostAddress() : null);
        if (remoteAddr != null && trustedProxies.contains(remoteAddr)) {
            String forwarded = request.getHeaders().getFirst(HEADER_FORWARDED_FOR);
            if (forwarded != null && !forwarded.isBlank()) {
                String[] hops = forwarded.split(",");
                for (int i = hops.length - 1; i >= 0; i--) {
                    String hop = hops[i].trim();
                    if (!hop.isEmpty()) {
                        return IpUtil.normalize(hop);
                    }
                }
            }
        }
        return remoteAddr != null ? remoteAddr : "unknown";
    }

    /** 设备指纹规范化：仅字母数字与-_，截断限长；清洗后为空视为未携带（不注入） */
    private String normalizeDeviceId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().replaceAll("[^A-Za-z0-9_-]", "");
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() > DEVICE_ID_MAX_LEN ? normalized.substring(0, DEVICE_ID_MAX_LEN) : normalized;
    }

    private boolean isWhitelisted(String path) {
        return whitelist.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private Mono<Void> reject(ServerWebExchange exchange, Result<Void> body) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] bytes = JacksonUtil.toJson(body).getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -10;
    }
}
