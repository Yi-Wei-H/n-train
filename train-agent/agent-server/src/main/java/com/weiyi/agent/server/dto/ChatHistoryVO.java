package com.weiyi.agent.server.dto;

import com.weiyi.agent.api.dto.AMessageDTO;
import lombok.Data;

import java.util.List;

/**
 * 用户端历史轮次分页（GET /ai/conversations/messages 出参）：
 * 按 beforeId 向更早翻页，一页固定若干轮；出参时间正序，前端上滑时前插。
 */
@Data
public class ChatHistoryVO {

    /** 本轮窗口的轮次（时间正序，旧→新） */
    private List<AMessageDTO> messages;

    /** 是否还有更早的轮次 */
    private Boolean hasMore;
}
