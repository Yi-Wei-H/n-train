package com.weiyi.agent.server.controller.rpc;

import com.weiyi.agent.api.api.AgentPresetApi;
import com.weiyi.agent.api.dto.AModelPresetDTO;
import com.weiyi.agent.api.dto.AModelPresetQuery;
import com.weiyi.agent.api.dto.AModelPresetReq;
import com.weiyi.agent.server.service.model.AModelPresetService;
import com.weiyi.common.core.result.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PresetController implements AgentPresetApi {

    private final AModelPresetService presetService;

    @Override
    public PageResult<AModelPresetDTO> list(AModelPresetQuery query) {
        return presetService.myModelPresets(query);
    }

    @Override
    public void createOrUpdate(@Valid AModelPresetReq req) {
        presetService.createOrUpdatePreset(req);
    }

    @Override
    public void updateStatus(Long id) {
        presetService.updateStatusById(id);
    }

    @Override
    public void deleteById(Long id) {
        presetService.deleteById(id);
    }
}
