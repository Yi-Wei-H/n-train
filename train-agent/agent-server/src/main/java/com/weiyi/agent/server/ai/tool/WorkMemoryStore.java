package com.weiyi.agent.server.ai.tool;

import com.weiyi.agent.server.config.AgentProperties;
import com.weiyi.common.redis.constant.RedisKeys;
import com.weiyi.common.redis.service.RedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 工作记忆存取：state/entity 两个 Redis 键，同一 TTL 滑动续期（时长 Nacos 配置）。
 * 读改写按"同账户单主回复串行"前提设计，不做并发版本控制。
 */
@Component
@RequiredArgsConstructor
public class WorkMemoryStore {

    private final RedisService redisService;
    private final AgentProperties agentProperties;

    public WorkState getState(Long conversationId) {
        WorkState state = redisService.get(RedisKeys.memoryState(conversationId), WorkState.class);
        return state != null ? state : new WorkState();
    }

    public void saveState(Long conversationId, WorkState state) {
        redisService.set(RedisKeys.memoryState(conversationId), state, ttl());
    }

    public WorkMemory getMemory(Long conversationId) {
        WorkMemory memory = redisService.get(RedisKeys.memoryEntity(conversationId), WorkMemory.class);
        return memory != null ? memory : new WorkMemory();
    }

    public void saveMemory(Long conversationId, WorkMemory memory) {
        redisService.set(RedisKeys.memoryEntity(conversationId), memory, ttl());
    }

    /** 更新分支子状态（不动顶层 state） */
    public void updateAgentSub(Long conversationId, String agentSub) {
        WorkState state = getState(conversationId);
        state.setAgentSub(agentSub);
        saveState(conversationId, state);
    }

    /** 写入/清除待确认单镜像；无既有实体且清除时为无操作 */
    public void setPendingConfirm(Long conversationId, WorkMemory.PendingConfirm pendingConfirm) {
        WorkMemory memory = redisService.get(RedisKeys.memoryEntity(conversationId), WorkMemory.class);
        if (memory == null) {
            if (pendingConfirm == null) {
                return;
            }
            memory = new WorkMemory();
        }
        memory.setPendingConfirm(pendingConfirm);
        saveMemory(conversationId, memory);
    }

    /** 翻篇/归档时整体删除工作记忆：状态与业务实体两键一并清除（TTL 兜底，显式删除加速失效） */
    public void delete(Long conversationId) {
        redisService.delete(RedisKeys.memoryState(conversationId));
        redisService.delete(RedisKeys.memoryEntity(conversationId));
    }

    private Duration ttl() {
        return Duration.ofMinutes(agentProperties.getMemory().getWorkTtlMinutes());
    }
}
