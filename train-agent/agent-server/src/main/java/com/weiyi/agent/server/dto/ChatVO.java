package com.weiyi.agent.server.dto;

import lombok.Data;

@Data
public class ChatVO {

    /** 会话 id */
    private Long conversationId;

    /** AI 回复消息 id */
    private Long messageId;

    /** 固定 assistant */
    private String role;

    /** AI 回复内容 */
    private String content;

    /** 本轮使用的文字模型 id */
    private Long chatModelId;

    /** 会话标题（meta/title/done 携带，未生成时为占位"新会话"） */
    private String title;

    /** 标题状态：NONE/GENERATING/SUCCESS/FAILED（meta/title/done 携带） */
    private String titleStatus;

    /** 仅 meta：true=服务端已翻篇，前端在本地消息流末尾插入"新会话"分隔后再接续 */
    private Boolean conversationReset;
}
