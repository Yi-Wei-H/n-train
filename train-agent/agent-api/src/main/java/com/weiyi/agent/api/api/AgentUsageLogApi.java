package com.weiyi.agent.api.api;

import com.weiyi.agent.api.dto.AUsageLogDTO;
import com.weiyi.agent.api.dto.AUsageLogQuery;
import com.weiyi.common.core.result.PageResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 用量流水接口（管理端，只读）：表不可变只增不改，配额按"用户+模型+当日成功行数"聚合。
 */
@FeignClient(name = "train-agent", contextId = "agentUsageLogApi")
public interface AgentUsageLogApi {

    /** 用量分页：userId/modelId/conversationId/callStatus 可选筛选 */
    @PostMapping("/ag/usage/list")
    PageResult<AUsageLogDTO> list(@RequestBody AUsageLogQuery query);
}
