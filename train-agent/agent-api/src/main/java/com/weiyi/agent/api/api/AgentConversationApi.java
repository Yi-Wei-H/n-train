package com.weiyi.agent.api.api;

import com.weiyi.agent.api.dto.AConversationDTO;
import com.weiyi.agent.api.dto.AConversationQuery;
import com.weiyi.common.core.result.PageResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 会话接口（管理端，只读 + 标题重生成）：账户级单主会话，创建只由发送链路发生，
 * 管理端不提供直建/直改；满 72h 未交互自动翻篇为归档（只读）。
 */
@FeignClient(name = "train-agent", contextId = "agentConversationApi")
public interface AgentConversationApi {

    /** 会话分页：id/userId/status 精确 + title 模糊，按最近消息时间降序 */
    @PostMapping("/ag/conversation/list")
    PageResult<AConversationDTO> list(@RequestBody AConversationQuery query);

    /** 逻辑删除 */
    @DeleteMapping("/ag/conversation/{id}")
    void deleteById(@PathVariable("id") long id);

    /** 重新生成标题（同步调用标题模型，慢接口；不受占位值 CAS 限制），成功返回新标题 */
    @PostMapping("/ag/conversation/regenerate-title")
    String regenerateTitle(@RequestParam("conversationId") long conversationId);
}
