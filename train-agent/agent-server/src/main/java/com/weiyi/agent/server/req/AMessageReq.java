package com.weiyi.agent.server.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AMessageReq {

    @NotNull(message = "会话 id 不能为空")
    private Long conversationId;

    /** 本轮触发方：user/assistant/system（白名单服务层校验） */
    @NotBlank(message = "触发方不能为空")
    @Size(max = 16, message = "触发方不能超过 16 字")
    private String role;

    /** 本轮输入：用户原文或卡片 prompt_text；system 轮为空 */
    private String userContent;

    /** 喂模型的净化版；空=回退原文 */
    private String userContentMasked;

    /** 便捷卡片 id：卡片发起的轮次记录来源 */
    private Long cardId;

    /** AI 回复；null=本轮调用失败 */
    private String reply;

    /** 本轮调用的工具清单（JSON 数组字符串，预留字段，工具调用未接入前恒空） */
    @Size(max = 512, message = "工具清单不能超过 512 字")
    private String tools;

    /** 文字模型 id */
    private Long chatModelId;

    /** 关联用量流水 id */
    private Long usageLogId;

    /** RAG 引用溯源（JSON 字符串） */
    @Size(max = 1024, message = "引用溯源不能超过 1024 字")
    private String refs;
}
