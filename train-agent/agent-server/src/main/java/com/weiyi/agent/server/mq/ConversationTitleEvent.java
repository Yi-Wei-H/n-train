package com.weiyi.agent.server.mq;

import lombok.Data;

import java.io.Serializable;

/** 会话标题生成事件（agent 域内自产自销）：会话创建后首轮投递，携带首条输入快照（此时消息尚未落库） */
@Data
public class ConversationTitleEvent implements Serializable {

    private Long conversationId;

    /** 首条输入快照：卡片轮为用户可见卡片名，文本轮为用户原文 */
    private String firstInput;
}
