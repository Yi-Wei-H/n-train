package com.weiyi.agent.api.api;

import com.weiyi.agent.api.dto.AUserModelDTO;
import com.weiyi.agent.api.dto.AUserModelQuery;
import com.weiyi.agent.api.dto.AUserModelReq;
import com.weiyi.common.core.result.PageResult;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 用户模型授权接口（管理端）：授权行随用户注册自动创建，管理面只编辑配额/状态与纠错删除。
 */
@FeignClient(name = "train-agent", contextId = "agentUserModelApi")
public interface AgentUserModelApi {

    /** 授权分页：userId / status 可选筛选 */
    @PostMapping("/ag/user-model/list")
    PageResult<AUserModelDTO> list(@RequestBody AUserModelQuery query);

    /** status / dailyQuota 非空覆盖；model_id 不经此口修改 */
    @PutMapping("/ag/user-model/update")
    void updateByUserId(@RequestBody @Valid AUserModelReq req);

    /** 纠错预留：删除用户授权行（原则上随用户生命周期管理） */
    @DeleteMapping("/ag/user-model/{userId}")
    void deleteByUserId(@PathVariable("userId") long userId);

    /** 幂等生成默认授权行（member 注册联动预留，body 为用户 id） */
    @PostMapping("/ag/user-model/create")
    void createByUserId(@RequestBody Long userId);
}
