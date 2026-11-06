package com.weiyi.agent.server.dto;

import java.util.List;

/**
 * 长期记忆提炼结构化输出：模型只产候选，是否采纳由服务端二次校验决定。
 * 自动任务只允许 UPSERT / NONE，无删除权。
 */
public record MemoryExtractionResult(List<Operation> operations) {

    /** 空结果（无值得提炼的信息 / 结构化调用最终失败的降级值） */
    public static MemoryExtractionResult empty() {
        return new MemoryExtractionResult(List.of());
    }

    /**
     * 单条候选。
     *
     * @param action     UPSERT 新增或更新 / NONE 不操作
     * @param memoryKey  规范键（seat_preference 等，稳定复用）
     * @param category   分类枚举名
     * @param content    单句客观陈述
     * @param evidence   判定依据（如"用户本轮明确表达"）
     * @param confidence 0~1
     */
    public record Operation(String action, String memoryKey, String category,
                            String content, String evidence, Double confidence) {
    }
}
