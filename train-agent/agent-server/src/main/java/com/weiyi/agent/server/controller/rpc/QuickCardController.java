package com.weiyi.agent.server.controller.rpc;

import com.weiyi.agent.api.api.AgentQuickCardApi;
import com.weiyi.agent.api.dto.AQuickCardDTO;
import com.weiyi.agent.api.dto.AQuickCardQuery;
import com.weiyi.agent.api.dto.AQuickCardReq;
import com.weiyi.agent.server.service.AQuickCardService;
import com.weiyi.common.core.result.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class QuickCardController implements AgentQuickCardApi {

    private final AQuickCardService quickCardService;

    @Override
    public PageResult<AQuickCardDTO> list(AQuickCardQuery query) {
        return quickCardService.list(query);
    }

    @Override
    public void createOrUpdate(@Valid AQuickCardReq req) {
        quickCardService.createOrUpdate(req);
    }

    @Override
    public void deleteById(long id) {
        quickCardService.deleteById(id);
    }
}
