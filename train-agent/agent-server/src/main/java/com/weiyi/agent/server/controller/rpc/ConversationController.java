package com.weiyi.agent.server.controller.rpc;

import com.weiyi.agent.api.api.AgentConversationApi;
import com.weiyi.agent.api.dto.AConversationDTO;
import com.weiyi.agent.api.dto.AConversationQuery;
import com.weiyi.agent.server.service.conversation.AConversationService;
import com.weiyi.agent.server.service.conversation.ConversationTitleService;
import com.weiyi.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ConversationController implements AgentConversationApi {

    private final AConversationService conversationService;
    private final ConversationTitleService conversationTitleService;

    @Override
    public PageResult<AConversationDTO> list(AConversationQuery query) {
        return conversationService.list(query);
    }

    @Override
    public void deleteById(long id) {
        conversationService.deleteById(id);
    }

    @Override
    public String regenerateTitle(long conversationId) {
        return conversationTitleService.regenerate(conversationId);
    }
}
