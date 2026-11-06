package com.weiyi.agent.server.ai.tool;

import tools.jackson.core.type.TypeReference;
import com.weiyi.agent.server.config.AgentProperties;
import com.weiyi.common.core.util.JacksonUtil;
import com.weiyi.common.redis.constant.RedisKeys;
import com.weiyi.common.redis.service.RedisService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 工具短期引用管理：listToken/ticketRef/passengerRef/orderRef/itemRef → 真实业务对象。
 * 引用值不可猜测且不含可反解的业务数据；绑定 userId + conversationId，
 * 与工作记忆同 TTL（键过期引用即整体失效），防止模型构造他人 id 或跨会话引用。
 * 存储为整文档 JSON 字符串，读改写整存整取（同账户串行前提下无并发冲突）。
 */
@Component
@RequiredArgsConstructor
public class ToolRefManager {

    private static final TypeReference<LinkedHashMap<String, RefEntry>> DOC_TYPE =
            new TypeReference<>() {
            };

    private final RedisService redisService;
    private final AgentProperties agentProperties;

    /** 保存一条引用，返回引用值 */
    public String save(Long conversationId, Long userId, String kind, Map<String, Object> payload) {
        String ref = randomRef();
        LinkedHashMap<String, RefEntry> doc = loadDoc(conversationId);
        RefEntry entry = new RefEntry();
        entry.setKind(kind);
        entry.setUserId(userId);
        entry.setPayload(payload);
        doc.put(ref, entry);
        redisService.set(RedisKeys.agentToolRef(conversationId), JacksonUtil.toJson(doc), ttl());
        return ref;
    }

    /** 解析引用：不存在 / 种类不符 / 归属不符均返回 null（不区分原因，不泄露目标是否存在） */
    public Map<String, Object> resolve(Long conversationId, Long userId, String ref, String expectedKind) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        RefEntry entry = loadDoc(conversationId).get(ref.trim());
        if (entry == null || !expectedKind.equals(entry.getKind()) || !userId.equals(entry.getUserId())) {
            return null;
        }
        return entry.getPayload();
    }

    private LinkedHashMap<String, RefEntry> loadDoc(Long conversationId) {
        String json = redisService.get(RedisKeys.agentToolRef(conversationId), String.class);
        if (json == null || json.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return JacksonUtil.fromJson(json, DOC_TYPE);
    }

    private static String randomRef() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private Duration ttl() {
        return Duration.ofMinutes(agentProperties.getMemory().getWorkTtlMinutes());
    }

    @Data
    public static class RefEntry {
        private String kind;
        private Long userId;
        private Map<String, Object> payload;
    }
}
