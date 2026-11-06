package com.weiyi.agent.api.api;

import com.weiyi.agent.api.dto.AModelPresetDTO;
import com.weiyi.agent.api.dto.AModelPresetQuery;
import com.weiyi.agent.api.dto.AModelPresetReq;
import com.weiyi.common.core.result.PageResult;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 模型预设接口（管理端）：预设仅作创建模型的模板，创建后互不影响。
 */
@FeignClient(name = "train-agent", contextId = "agentPresetApi")
public interface AgentPresetApi {

    /** 预设分页：按模型类型筛选 */
    @PostMapping("/ag/preset/list")
    PageResult<AModelPresetDTO> list(@RequestBody AModelPresetQuery query);

    /** 预设维护 */
    @PostMapping("/ag/preset/create-or-update")
    void createOrUpdate(@RequestBody @Valid AModelPresetReq req);

    /** 上架/下架 */
    @PutMapping("/ag/preset/{id}/status")
    void updateStatus(@PathVariable("id") Long id);

    /** 逻辑删除 */
    @DeleteMapping("/ag/preset/{id}")
    void deleteById(@PathVariable("id") Long id);
}
