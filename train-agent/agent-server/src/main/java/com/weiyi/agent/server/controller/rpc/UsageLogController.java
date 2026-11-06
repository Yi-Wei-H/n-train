package com.weiyi.agent.server.controller.rpc;

import com.weiyi.agent.api.api.AgentUsageLogApi;
import com.weiyi.agent.api.dto.AUsageLogDTO;
import com.weiyi.agent.api.dto.AUsageLogQuery;
import com.weiyi.agent.server.service.AUsageLogService;
import com.weiyi.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UsageLogController implements AgentUsageLogApi {

    private final AUsageLogService usageLogService;

    @Override
    public PageResult<AUsageLogDTO> list(AUsageLogQuery query) {
        return usageLogService.list(query);
    }
}
