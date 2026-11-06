package com.weiyi.agent.server.controller;

import com.weiyi.agent.server.dto.AUserMemoryDTO;
import com.weiyi.agent.server.entity.AUserMemory;
import com.weiyi.agent.server.entity.AUserMemorySetting;
import com.weiyi.agent.server.req.AUserMemoryQuery;
import com.weiyi.agent.server.req.AUserMemoryReq;
import com.weiyi.agent.server.req.AUserMemorySettingReq;
import com.weiyi.agent.server.service.memory.MemoryService;
import com.weiyi.common.core.result.PageResult;
import com.weiyi.common.core.result.Result;
import com.weiyi.common.redis.ratelimit.LimitDimension;
import com.weiyi.common.redis.ratelimit.RateLimit;
import com.weiyi.common.security.context.LoginContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 记忆管理（用户侧）：查看/新增/修改/删除/清空/总开关，全部按 LoginContext 身份限定账户范围。
 * 写接口合并限流（USER 30/分钟，严格模式）；所有变更返回服务端最新版本。
 */
@RestController
@RequestMapping("ai/memories")
@RequiredArgsConstructor
public class AUserMemoryController {

    private final MemoryService memoryService;

    /** 分页查看当前账户记忆（分类/状态可选筛选） */
    @PostMapping("list")
    public Result<PageResult<AUserMemoryDTO>> list(@RequestBody AUserMemoryQuery query) {
        return Result.ok(memoryService.list(LoginContext.requireMemberId(), query));
    }

    /** 手动新增（与自动提炼共用校验与去重） */
    @PostMapping
    @RateLimit(dimension = LimitDimension.USER, count = 30, interval = 60, strict = true,
            message = "操作过于频繁，请稍后再试")
    public Result<AUserMemoryDTO> create(@RequestBody AUserMemoryReq req) {
        AUserMemory memory = memoryService.createManual(LoginContext.requireMemberId(), req);
        return Result.ok(memoryService.getOwned(LoginContext.requireMemberId(), memory.getId()));
    }

    /** 修改 content 走完整校验；status 可单独提交；version 冲突提示刷新 */
    @PutMapping("{id}")
    @RateLimit(dimension = LimitDimension.USER, count = 30, interval = 60, strict = true,
            message = "操作过于频繁，请稍后再试")
    public Result<AUserMemoryDTO> update(@PathVariable Long id, @RequestBody AUserMemoryReq req) {
        memoryService.update(LoginContext.requireMemberId(), id, req);
        return Result.ok(memoryService.getOwned(LoginContext.requireMemberId(), id));
    }

    /** 删除单条（物理删除 + 纪元递增，防在途提炼复活） */
    @DeleteMapping("{id}")
    @RateLimit(dimension = LimitDimension.USER, count = 30, interval = 60, strict = true,
            message = "操作过于频繁，请稍后再试")
    public Result<Void> delete(@PathVariable Long id) {
        memoryService.delete(LoginContext.requireMemberId(), id);
        return Result.ok();
    }

    /** 清空当前账户全部记忆（前端需二次确认后调用） */
    @DeleteMapping
    @RateLimit(dimension = LimitDimension.USER, count = 30, interval = 60, strict = true,
            message = "操作过于频繁，请稍后再试")
    public Result<Void> clear() {
        memoryService.clear(LoginContext.requireMemberId());
        return Result.ok();
    }

    /** 总开关设置（关闭/重置递增纪元），返回最新设置 */
    @PutMapping("setting")
    @RateLimit(dimension = LimitDimension.USER, count = 30, interval = 60, strict = true,
            message = "操作过于频繁，请稍后再试")
    public Result<AUserMemorySetting> updateSetting(@RequestBody AUserMemorySettingReq req) {
        return Result.ok(memoryService.updateSetting(LoginContext.requireMemberId(),
                req.getEnabled(), req.getVersion()));
    }
}
