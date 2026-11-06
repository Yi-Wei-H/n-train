package com.weiyi.agent.server.controller;

import com.weiyi.agent.server.dto.AQuickCardShowDTO;
import com.weiyi.agent.server.service.AQuickCardService;
import com.weiyi.common.core.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 便捷卡片用户端查询：卡片维护走管理面 RPC 契约（QuickCardController），本类只保留网关用户侧入口。
 */
@RestController
@RequestMapping("quick-card")
@RequiredArgsConstructor
public class AQuickCardController {

    private final AQuickCardService quickCardService;

    /** 用户端展示查询：上架且活动有效（空=常驻），只回 id/标题/图标/排序 */
    @GetMapping("active")
    public Result<List<AQuickCardShowDTO>> listActive() {
        return Result.ok(quickCardService.listActive());
    }
}
