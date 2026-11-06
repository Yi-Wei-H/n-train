package com.weiyi.agent.server.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AUsageLogReq {

    /** 流水 id；更新必填 */
    private Long id;

    @NotNull(message = "用户 id 不能为空")
    private Long userId;

    @NotNull(message = "模型 id 不能为空")
    private Long modelId;

    /** 关联会话ID；独立调用（如连通性测试）为空 */
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
    @NotNull(message = "调用结果不能为空")
    private Integer callStatus;

    /** 失败原因 */
    private String errMsg;
}
