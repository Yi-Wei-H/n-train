package com.weiyi.agent.api.api;

import com.weiyi.agent.api.dto.AMessageDTO;
import com.weiyi.agent.api.dto.AMessageQuery;
import com.weiyi.common.core.result.PageResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 会话消息接口（管理端，只读）：一条消息 = 一轮对话；不传 role 默认排除 system 轮，
 * 管理端显式传 role=system 可查系统任务轮——这是管理面专属能力。
 */
@FeignClient(name = "train-agent", contextId = "agentMessageApi")
public interface AgentMessageApi {

    /** 按会话查轮次：conversationId 必传，role/chatModelId 可选筛选，时间正序 */
    @PostMapping("/ag/message/list")
    PageResult<AMessageDTO> list(@RequestBody AMessageQuery query);
}
