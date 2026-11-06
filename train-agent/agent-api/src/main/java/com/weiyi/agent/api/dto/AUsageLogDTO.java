package com.weiyi.agent.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AUsageLogDTO {

    private Long id;

    private LocalDateTime createdAt;

    private Long userId;

    /** 调用的模型 ID */
    private Long modelId;

    /** 关联会话ID */
    private Long conversationId;

    /** 新增输入 token 数（未命中缓存的输入） */
    private Integer inputTokens;

    /** 缓存命中 token 数 */
    private Integer cachedTokens;

    /** 输出 token 数 */
    private Integer outputTokens;

    /** 调用耗时（毫秒） */
    private Integer latencyMs;

    /** 调用结果：1成功 2失败 */
    private Integer callStatus;

    /** 失败原因 */
    private String errMsg;
}
