package com.weiyi.agent.api.api;

import com.weiyi.agent.api.dto.AModelDTO;
import com.weiyi.agent.api.dto.AModelQuery;
import com.weiyi.agent.api.dto.AModelReq;
import com.weiyi.agent.api.dto.ModelListDTO;
import com.weiyi.agent.api.dto.ModelListReq;
import com.weiyi.agent.api.dto.ModelTestDTO;
import com.weiyi.common.core.result.PageResult;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 模型配置接口（管理端）：预设接入即全量快照，api_key 传输层 AES 加密、出参全量回显由前端控制展示。
 * test / listRemote 为上游 LLM 同步调用（慢接口），消费端需放宽读超时。
 */
@FeignClient(name = "train-agent", contextId = "agentModelApi")
public interface AgentModelApi {

    /** 新增/全量快照保存（编辑 apiKey 传空沿用原值；新增默认停用） */
    @PostMapping("/ag/model/create-or-update")
    void createOrUpdate(@RequestBody @Valid AModelReq req);

    /** 模型分页：按模型类型筛选 */
    @PostMapping("/ag/model/list")
    PageResult<AModelDTO> list(@RequestBody AModelQuery query);

    /** 逻辑删除 */
    @DeleteMapping("/ag/model/{id}")
    void deleteById(@PathVariable("id") long id);

    /** 启用/停用切换（同类型互斥：启用会停用当前启用的同类型模型） */
    @PutMapping("/ag/model/{id}/status")
    void updateStatusById(@PathVariable("id") long id);

    /** 连通性测试：成功返回耗时，失败返回错误归类文案；向量模型附返回维度 */
    @PostMapping("/ag/model/{id}/test")
    ModelTestDTO test(@PathVariable("id") long id);

    /** 按 baseURL/key 拉取上游模型列表（配置辅助）；"拉不到"是正常降级（success=false） */
    @PostMapping("/ag/model/list-remote")
    ModelListDTO listRemote(@RequestBody ModelListReq req);
}
