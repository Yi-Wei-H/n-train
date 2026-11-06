package com.weiyi.agent.server.controller.rpc;

import com.weiyi.agent.api.api.AgentUserModelApi;
import com.weiyi.agent.api.dto.AUserModelDTO;
import com.weiyi.agent.api.dto.AUserModelQuery;
import com.weiyi.agent.api.dto.AUserModelReq;
import com.weiyi.agent.server.service.model.AUserModelService;
import com.weiyi.common.core.result.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserModelController implements AgentUserModelApi {

    private final AUserModelService userModelService;

    @Override
    public PageResult<AUserModelDTO> list(AUserModelQuery query) {
        return userModelService.list(query);
    }

    @Override
    public void updateByUserId(@Valid AUserModelReq req) {
        userModelService.updateByUserId(req);
    }

    @Override
    public void deleteByUserId(long userId) {
        userModelService.deleteByUserId(userId);
    }

    @Override
    public void createByUserId(Long userId) {
        userModelService.createByUserId(userId);
    }
}
