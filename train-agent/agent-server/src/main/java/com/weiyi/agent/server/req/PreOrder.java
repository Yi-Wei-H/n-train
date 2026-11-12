package com.weiyi.agent.server.req;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PreOrder {
    @NotNull(message = "会话 id 不能为空")
    private Long conversationId;
    /** CONFIRM;CANCEL */
    @NotNull(message = "操作标识不能为空")
    private String action;
}
