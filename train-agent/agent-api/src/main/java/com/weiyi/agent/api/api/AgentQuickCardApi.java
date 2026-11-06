package com.weiyi.agent.api.api;

import com.weiyi.agent.api.dto.AQuickCardDTO;
import com.weiyi.agent.api.dto.AQuickCardQuery;
import com.weiyi.agent.api.dto.AQuickCardReq;
import com.weiyi.common.core.result.PageResult;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 便捷卡片接口（管理端）：卡片在用户端点击即发送隐藏提示词，只回传 cardId；
 * 用户端有效卡片查询（active）走网关用户侧，不在本契约。
 */
@FeignClient(name = "train-agent", contextId = "agentQuickCardApi")
public interface AgentQuickCardApi {

    /** 卡片分页：cardName 模糊 + status 精确 */
    @PostMapping("/ag/card/list")
    PageResult<AQuickCardDTO> list(@RequestBody AQuickCardQuery query);

    /** 全量快照保存（icon/活动窗口传空即清空） */
    @PostMapping("/ag/card/create-or-update")
    void createOrUpdate(@RequestBody @Valid AQuickCardReq req);

    /** 逻辑删除 */
    @DeleteMapping("/ag/card/{id}")
    void deleteById(@PathVariable("id") long id);
}
