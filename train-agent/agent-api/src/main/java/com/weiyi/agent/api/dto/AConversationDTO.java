package com.weiyi.agent.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AConversationDTO {

    private Long id;

    private Long userId;

    private String title;

    /** 文字模型 id；空=跟随当前启用模型 */
    private Long chatModelId;

    /** 消息数 */
    private Integer messageCount;

    /** 最近消息时间 */
    private LocalDateTime lastMessageAt;

    /** 最近有效用户交互时间（72h 翻篇判定锚点） */
    private LocalDateTime lastInteractionAt;

    /** 标题状态：0未触发 1生成中 2成功 3最终失败 */
    private Integer titleStatus;

    /** 状态：1正常 2归档 */
    private Integer status;
}
