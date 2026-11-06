package com.weiyi.agent.server.ai.provider;

import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.api.dto.ModelItemDTO;
import com.weiyi.agent.api.dto.ModelListDTO;
import com.weiyi.agent.api.dto.ModelListReq;
import com.weiyi.common.core.util.JacksonUtil;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 上游模型列表获取器：裸 HTTP + 宽松解析，不经过 LlmProviderRegistry / 官方 SDK
 * （SDK 构建时 baseUrl 锁死，无法实现候选 URL 依次尝试；强类型响应对智谱 models[].slug
 * 等形状会静默解析成空列表）。设计定稿见 doc/模型列表获取设计.md。
 *
 * <p>核心策略：候选 URL 按序尝试，404/405 才换下一个（认证对了路径不对才值得换），
 * 401/403/5xx/网络错误立即终止；错误体先脱敏 apiKey 再截断（防整页 HTML 404）。
 */
@Slf4j
public final class ModelListFetcher {

    /** 拉列表无长推理，专用短超时（不共用 chat 的 300s 长读超时） */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    /** 目录上限，防异常大目录撑爆响应；超出截断并置 truncated */
    private static final int MAX_MODELS = 1000;

    /** 错误响应体截断长度（cc-switch 同款，防整页 HTML 404 进错误串） */
    private static final int ERROR_BODY_MAX_CHARS = 512;

    /** Anthropic 官方端点分页（limit 默认 20），一次拉全 */
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int ANTHROPIC_LIST_LIMIT = 1000;

    /**
     * 已知「Anthropic 协议兼容子路径」后缀，长度降序——最长优先匹配，
     * 防将来加入的 /api/anthropic 只剥掉 /anthropic 残缺出 .../api 根。
     */
    private static final List<String> COMPAT_SUFFIXES = List.of("/claudecode", "/anthropic", "/coding", "/claude");

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    private ModelListFetcher() {
    }

    public static ModelListDTO fetch(ModelListReq req) {
        List<String> candidates = buildCandidates(req.getBaseUrl());
        String lastEndpointError = null;
        for (String url : candidates) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(buildUri(url, req))
                        .timeout(READ_TIMEOUT)
                        .header("Accept", "application/json")
                        .GET();
                for (String[] header : authHeaders(req)) {
                    builder.header(header[0], header[1]);
                }
                HttpResponse<String> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());

                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    return toResult(response.body(), req);
                }
                String body = redact(response.body(), req.getApiKey());
                if (status == 404 || status == 405) {
                    lastEndpointError = "HTTP " + status + ": " + body;
                    continue;
                }
                // 401/403/5xx 等：立即终止（认证错了换路径也没用），按状态码+报文走统一归类
                return fail(ErrorClassifier.classify(new RuntimeException("HTTP " + status + ": " + body)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return fail(ErrorClassifier.classify(e));
            } catch (Exception e) {
                return fail(ErrorClassifier.classify(e));
            }
        }
        log.warn("模型列表候选端点全部未命中: baseUrl={}, lastError={}", req.getBaseUrl(), lastEndpointError);
        return fail("上游未实现模型列表接口或 base_url 有误，请手填 api_model_id");
    }

    /** 2xx 响应体 → 结果：data[] 优先、models[].slug/id 兜底；无法识别的形状按降级处理 */
    private static ModelListDTO toResult(String body, ModelListReq req) {
        List<ModelItemDTO> models = parseModels(body);
        if (models == null) {
            log.warn("模型列表响应格式无法解析: baseUrl={}", req.getBaseUrl());
            return fail("上游响应格式无法解析，请手填 api_model_id");
        }
        models.sort(Comparator.comparing(ModelItemDTO::getId));
        String keyword = req.getKeyword();
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.toLowerCase();
            models.removeIf(m -> !containsKeyword(m, k));
        }
        boolean truncated = models.size() > MAX_MODELS;
        if (truncated) {
            models = new ArrayList<>(models.subList(0, MAX_MODELS));
        }
        ModelListDTO dto = new ModelListDTO();
        dto.setSuccess(true);
        dto.setModels(models);
        dto.setTruncated(truncated);
        return dto;
    }

    private static boolean containsKeyword(ModelItemDTO m, String lowerKeyword) {
        return (m.getId() != null && m.getId().toLowerCase().contains(lowerKeyword))
                || (m.getOwnedBy() != null && m.getOwnedBy().toLowerCase().contains(lowerKeyword))
                || (m.getDisplayName() != null && m.getDisplayName().toLowerCase().contains(lowerKeyword));
    }

    /**
     * 宽松解析三种形状：OpenAI 系 data[].{id, owned_by?, display_name?}、
     * Anthropic 系 data[].{id, display_name?}、智谱 Responses models[].slug（回退 id）。
     * data 与 models 并存时 data 优先；条目缺 id/非字符串一律跳过不报错。
     *
     * @return 归一列表；既无 data 又无 models、或 JSON 非法时返回 null（降级）
     */
    static List<ModelItemDTO> parseModels(String body) {
        try {
            JsonNode root = JacksonUtil.MAPPER.readTree(body);
            JsonNode data = root.get("data");
            if (data != null && data.isArray()) {
                List<ModelItemDTO> models = new ArrayList<>();
                for (JsonNode item : data) {
                    String id = asText(item.get("id"));
                    if (id == null || id.isBlank()) {
                        continue;
                    }
                    models.add(new ModelItemDTO(id, asText(item.get("owned_by")), asText(item.get("display_name"))));
                }
                return models;
            }
            JsonNode modelsNode = root.get("models");
            if (modelsNode != null && modelsNode.isArray()) {
                List<ModelItemDTO> models = new ArrayList<>();
                for (JsonNode item : modelsNode) {
                    String id = asText(item.get("slug"));
                    if (id == null) {
                        id = asText(item.get("id"));
                    }
                    if (id == null || id.isBlank()) {
                        continue;
                    }
                    models.add(new ModelItemDTO(id, null, null));
                }
                return models;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 候选 URL 生成（去重保持顺序）：
     * 1. 版本段结尾（/v1、/v4 等）→ {base}/models（非 /v1 追加 {base}/v1/models 次候选）；
     * 2. 否则 → {base}/v1/models；
     * 3. 命中兼容子路径后缀 → 剥离后追加 {root}/v1/models、{root}/models（DeepSeek /anthropic 场景）。
     */
    static List<String> buildCandidates(String baseUrl) {
        String trimmed = CorrectBaseURL.stripTrailingSlashes(baseUrl);
        List<String> candidates = new ArrayList<>();
        if (CorrectBaseURL.endsWithVersionSegment(trimmed)) {
            candidates.add(trimmed + "/models");
            if (!trimmed.endsWith("/v1")) {
                candidates.add(trimmed + "/v1/models");
            }
        } else {
            candidates.add(trimmed + "/v1/models");
        }
        String stripped = stripCompatSuffix(trimmed);
        if (stripped != null) {
            candidates.add(stripped + "/v1/models");
            candidates.add(stripped + "/models");
        }
        return candidates.stream().distinct().toList();
    }

    /** 命中已知兼容子路径则返回剥离后的根（须仍是 scheme://host 形态），否则 null */
    private static String stripCompatSuffix(String baseUrl) {
        for (String suffix : COMPAT_SUFFIXES) {
            if (baseUrl.endsWith(suffix)) {
                String root = baseUrl.substring(0, baseUrl.length() - suffix.length());
                if (root.contains("://") && root.length() > root.indexOf("://") + 3) {
                    return root;
                }
                return null;
            }
        }
        return null;
    }

    /** 拼认证/分页查询参数：authType=3 的 key 参数与 Anthropic 的 limit */
    private static URI buildUri(String url, ModelListReq req) {
        StringBuilder query = new StringBuilder();
        if (AgentConstants.AUTH_QUERY == req.getAuthType()) {
            query.append(req.getAuthKeyName().trim()).append('=')
                    .append(URLEncoder.encode(req.getApiKey(), StandardCharsets.UTF_8));
        }
        if (AgentConstants.API_FORMAT_ANTHROPIC == req.getApiFormat()) {
            query.append(query.length() > 0 ? "&" : "").append("limit=").append(ANTHROPIC_LIST_LIMIT);
        }
        return URI.create(url + (query.length() > 0 ? "?" + query : ""));
    }

    /** 认证头拼装（两维语义同 ApiAuthInjector，但不经 SDK 的 ClientOptions）：Bearer / 头直传，Anthropic 附版本头 */
    private static List<String[]> authHeaders(ModelListReq req) {
        List<String[]> headers = new ArrayList<>();
        if (AgentConstants.AUTH_BEARER == req.getAuthType()) {
            headers.add(new String[]{"Authorization", "Bearer " + req.getApiKey()});
        } else if (AgentConstants.AUTH_HEADER == req.getAuthType()) {
            headers.add(new String[]{req.getAuthKeyName().trim(), req.getApiKey()});
        }
        if (AgentConstants.API_FORMAT_ANTHROPIC == req.getApiFormat()) {
            headers.add(new String[]{"anthropic-version", ANTHROPIC_VERSION});
        }
        return headers;
    }

    /** 错误体先脱敏 apiKey 再截断，防止 key 泄进错误串、整页 HTML 404 占满 errMsg */
    static String redact(String body, String secret) {
        String result = body == null ? "" : body;
        if (secret != null && !secret.isBlank()) {
            result = result.replace(secret, "[REDACTED]");
        }
        return result.length() <= ERROR_BODY_MAX_CHARS ? result
                : result.substring(0, ERROR_BODY_MAX_CHARS) + "…";
    }

    private static String asText(JsonNode node) {
        return node != null && node.isTextual() ? node.asString() : null;
    }

    private static ModelListDTO fail(String errMsg) {
        ModelListDTO dto = new ModelListDTO();
        dto.setSuccess(false);
        dto.setErrMsg(errMsg);
        dto.setTruncated(false);
        return dto;
    }
}
