package com.weiyi.agent.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AMessageDTO {

    private Long id;

    private LocalDateTime createdAt;

    private Long conversationId;

    /** 本轮触发方：user 用户 / assistant AI / system 系统自动 */
    private String role;

    /** 本轮输入：用户原文或卡片 prompt_text */
    private String userContent;

    /** 喂模型的净化版：仅服务端内部消费，不出历史接口出参 */
    @JsonIgnore
    private String userContentMasked;

    /** 喂模型的内容口径：净化版优先，空回退原文 */
    public String modelContent() {
        return userContentMasked != null && !userContentMasked.isBlank() ? userContentMasked : userContent;
    }

    /** 便捷卡片 id */
    private Long cardId;

    /** AI 回复；null=本轮调用失败 */
    private String reply;

    /** 本轮调用的工具清单（JSON 数组字符串） */
    private String tools;

    private Long chatModelId;

    /** 关联用量流水 id（token 用量在 a_usage_log） */
    private Long usageLogId;

    /** RAG 引用溯源（JSON 字符串） */
    private String refs;
}
