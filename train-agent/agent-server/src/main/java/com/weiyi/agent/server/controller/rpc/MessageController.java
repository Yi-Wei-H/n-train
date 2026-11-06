package com.weiyi.agent.server.controller.rpc;

import com.weiyi.agent.api.api.AgentMessageApi;
import com.weiyi.agent.api.dto.AMessageDTO;
import com.weiyi.agent.api.dto.AMessageQuery;
import com.weiyi.agent.server.service.conversation.AMessageService;
import com.weiyi.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MessageController implements AgentMessageApi {

    private final AMessageService messageService;

    @Override
    public PageResult<AMessageDTO> list(AMessageQuery query) {
        return messageService.list(query);
    }
}
