package com.weiyi.agent.server.controller.rpc;

import com.weiyi.agent.api.api.AgentModelApi;
import com.weiyi.agent.api.dto.AModelDTO;
import com.weiyi.agent.api.dto.AModelQuery;
import com.weiyi.agent.api.dto.AModelReq;
import com.weiyi.agent.api.dto.ModelListDTO;
import com.weiyi.agent.api.dto.ModelListReq;
import com.weiyi.agent.api.dto.ModelTestDTO;
import com.weiyi.agent.server.service.model.AModelService;
import com.weiyi.agent.server.service.model.ModelListService;
import com.weiyi.agent.server.service.model.ModelTestService;
import com.weiyi.common.core.result.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ModelController implements AgentModelApi {

    private final AModelService aModelService;
    private final ModelTestService modelTestService;
    private final ModelListService modelListService;

    @Override
    public void createOrUpdate(AModelReq req) {
        aModelService.createOrUpdateModel(req);
    }

    @Override
    public PageResult<AModelDTO> list(AModelQuery query) {
        return aModelService.list(query);
    }

    @Override
    public void deleteById(long id) {
        aModelService.deleteById(id);
    }

    @Override
    public void updateStatusById(long id) {
        aModelService.updateStatusById(id);
    }

    @Override
    public ModelTestDTO test(long id) {
        return modelTestService.testModel(id);
    }

    @Override
    public ModelListDTO listRemote(ModelListReq req) {
        return modelListService.listRemoteModels(req);
    }
}
