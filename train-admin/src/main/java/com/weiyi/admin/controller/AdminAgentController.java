package com.weiyi.admin.controller;

import com.weiyi.agent.api.api.AgentConversationApi;
import com.weiyi.agent.api.api.AgentMessageApi;
import com.weiyi.agent.api.api.AgentModelApi;
import com.weiyi.agent.api.api.AgentPresetApi;
import com.weiyi.agent.api.api.AgentQuickCardApi;
import com.weiyi.agent.api.api.AgentUsageLogApi;
import com.weiyi.agent.api.api.AgentUserModelApi;
import com.weiyi.agent.api.dto.AConversationDTO;
import com.weiyi.agent.api.dto.AConversationQuery;
import com.weiyi.agent.api.dto.AMessageDTO;
import com.weiyi.agent.api.dto.AMessageQuery;
import com.weiyi.agent.api.dto.AModelDTO;
import com.weiyi.agent.api.dto.AModelPresetDTO;
import com.weiyi.agent.api.dto.AModelPresetQuery;
import com.weiyi.agent.api.dto.AModelPresetReq;
import com.weiyi.agent.api.dto.AModelQuery;
import com.weiyi.agent.api.dto.AModelReq;
import com.weiyi.agent.api.dto.AQuickCardDTO;
import com.weiyi.agent.api.dto.AQuickCardQuery;
import com.weiyi.agent.api.dto.AQuickCardReq;
import com.weiyi.agent.api.dto.AUsageLogDTO;
import com.weiyi.agent.api.dto.AUsageLogQuery;
import com.weiyi.agent.api.dto.AUserModelDTO;
import com.weiyi.agent.api.dto.AUserModelQuery;
import com.weiyi.agent.api.dto.AUserModelReq;
import com.weiyi.agent.api.dto.ModelListDTO;
import com.weiyi.agent.api.dto.ModelListReq;
import com.weiyi.agent.api.dto.ModelTestDTO;
import com.weiyi.common.core.result.PageResult;
import com.weiyi.common.core.result.Result;
import com.weiyi.admin.operlog.OperLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 模块管理端入口：Feign 转发 agent 契约并统一 Shiro 权限收口，
 * 错误码（agent 段 44xxx~47xxx）经 ErrorDecoder 原样透传给管理前端。
 */
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AdminAgentController {

    private final AgentModelApi agentModelApi;
    private final AgentPresetApi agentPresetApi;
    private final AgentUserModelApi agentUserModelApi;
    private final AgentConversationApi agentConversationApi;
    private final AgentMessageApi agentMessageApi;
    private final AgentQuickCardApi agentQuickCardApi;
    private final AgentUsageLogApi agentUsageLogApi;

    // ---------- 模型配置 ----------

    @PostMapping("/model/list")
    @RequiresPermissions("a:model:list")
    public Result<PageResult<AModelDTO>> modelList(@RequestBody AModelQuery query) {
        return Result.ok(agentModelApi.list(query));
    }

    @PostMapping("/model")
    @RequiresPermissions("a:model:save")
    @OperLog(module = "AI模型管理", operation = "保存模型配置")
    public Result<Void> modelSave(@RequestBody @Valid AModelReq req) {
        agentModelApi.createOrUpdate(req);
        return Result.ok();
    }

    @DeleteMapping("/model/{id}")
    @RequiresPermissions("a:model:delete")
    @OperLog(module = "AI模型管理", operation = "删除模型配置")
    public Result<Void> modelDelete(@PathVariable long id) {
        agentModelApi.deleteById(id);
        return Result.ok();
    }

    @PutMapping("/model/{id}/status")
    @RequiresPermissions("a:model:save")
    @OperLog(module = "AI模型管理", operation = "切换模型状态")
    public Result<Void> modelToggleStatus(@PathVariable long id) {
        agentModelApi.updateStatusById(id);
        return Result.ok();
    }

    /** 连通性测试：上游 LLM 同步调用（慢接口，消费端超时已放宽） */
    @PostMapping("/model/{id}/test")
    @RequiresPermissions("a:model:test")
    public Result<ModelTestDTO> modelTest(@PathVariable long id) {
        return Result.ok(agentModelApi.test(id));
    }

    /** 拉取上游模型列表（配置辅助） */
    @PostMapping("/model/list-remote")
    @RequiresPermissions("a:model:save")
    public Result<ModelListDTO> modelListRemote(@RequestBody ModelListReq req) {
        return Result.ok(agentModelApi.listRemote(req));
    }

    // ---------- 模型预设 ----------

    @PostMapping("/preset/list")
    @RequiresPermissions("a:preset:list")
    public Result<PageResult<AModelPresetDTO>> presetList(@RequestBody AModelPresetQuery query) {
        return Result.ok(agentPresetApi.list(query));
    }

    @PostMapping("/preset")
    @RequiresPermissions("a:preset:save")
    @OperLog(module = "AI预设管理", operation = "保存模型预设")
    public Result<Void> presetSave(@RequestBody @Valid AModelPresetReq req) {
        agentPresetApi.createOrUpdate(req);
        return Result.ok();
    }

    @PutMapping("/preset/{id}/status")
    @RequiresPermissions("a:preset:save")
    @OperLog(module = "AI预设管理", operation = "切换预设状态")
    public Result<Void> presetToggleStatus(@PathVariable Long id) {
        agentPresetApi.updateStatus(id);
        return Result.ok();
    }

    @DeleteMapping("/preset/{id}")
    @RequiresPermissions("a:preset:delete")
    @OperLog(module = "AI预设管理", operation = "删除模型预设")
    public Result<Void> presetDelete(@PathVariable Long id) {
        agentPresetApi.deleteById(id);
        return Result.ok();
    }

    // ---------- 用户授权 ----------

    @PostMapping("/user-model/list")
    @RequiresPermissions("a:user-model:list")
    public Result<PageResult<AUserModelDTO>> userModelList(@RequestBody AUserModelQuery query) {
        return Result.ok(agentUserModelApi.list(query));
    }

    @PutMapping("/user-model")
    @RequiresPermissions("a:user-model:save")
    @OperLog(module = "用户模型授权", operation = "保存用户授权")
    public Result<Void> userModelUpdate(@RequestBody @Valid AUserModelReq req) {
        agentUserModelApi.updateByUserId(req);
        return Result.ok();
    }

    @DeleteMapping("/user-model/{userId}")
    @RequiresPermissions("a:user-model:delete")
    @OperLog(module = "用户模型授权", operation = "删除用户授权")
    public Result<Void> userModelDelete(@PathVariable long userId) {
        agentUserModelApi.deleteByUserId(userId);
        return Result.ok();
    }

    // ---------- 会话管理 ----------

    @PostMapping("/conversation/list")
    @RequiresPermissions("a:conversation:list")
    public Result<PageResult<AConversationDTO>> conversationList(@RequestBody AConversationQuery query) {
        return Result.ok(agentConversationApi.list(query));
    }

    @DeleteMapping("/conversation/{id}")
    @RequiresPermissions("a:conversation:delete")
    @OperLog(module = "AI会话管理", operation = "删除会话")
    public Result<Void> conversationDelete(@PathVariable long id) {
        agentConversationApi.deleteById(id);
        return Result.ok();
    }

    /** 重新生成标题：同步调用标题模型（慢接口） */
    @PostMapping("/conversation/regenerate-title")
    @RequiresPermissions("a:conversation:regenerate-title")
    @OperLog(module = "AI会话管理", operation = "重新生成会话标题")
    public Result<String> conversationRegenerateTitle(@RequestParam long conversationId) {
        return Result.ok(agentConversationApi.regenerateTitle(conversationId));
    }

    // ---------- 会话消息 ----------

    @PostMapping("/message/list")
    @RequiresPermissions("a:message:list")
    public Result<PageResult<AMessageDTO>> messageList(@RequestBody AMessageQuery query) {
        return Result.ok(agentMessageApi.list(query));
    }

    // ---------- 便捷卡片 ----------

    @PostMapping("/card/list")
    @RequiresPermissions("a:card:list")
    public Result<PageResult<AQuickCardDTO>> cardList(@RequestBody AQuickCardQuery query) {
        return Result.ok(agentQuickCardApi.list(query));
    }

    @PostMapping("/card")
    @RequiresPermissions("a:card:save")
    @OperLog(module = "便捷卡片", operation = "保存卡片")
    public Result<Void> cardSave(@RequestBody @Valid AQuickCardReq req) {
        agentQuickCardApi.createOrUpdate(req);
        return Result.ok();
    }

    @DeleteMapping("/card/{id}")
    @RequiresPermissions("a:card:delete")
    @OperLog(module = "便捷卡片", operation = "删除卡片")
    public Result<Void> cardDelete(@PathVariable long id) {
        agentQuickCardApi.deleteById(id);
        return Result.ok();
    }

    // ---------- 用量统计 ----------

    @PostMapping("/usage/list")
    @RequiresPermissions("a:usage:list")
    public Result<PageResult<AUsageLogDTO>> usageList(@RequestBody AUsageLogQuery query) {
        return Result.ok(agentUsageLogApi.list(query));
    }
}
