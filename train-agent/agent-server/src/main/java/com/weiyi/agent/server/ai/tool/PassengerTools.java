package com.weiyi.agent.server.ai.tool;

import com.weiyi.agent.server.constant.ToolConstants;
import com.weiyi.member.api.api.PassengerApi;
import com.weiyi.member.api.dto.MemberPassengerDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 乘车人只读工具（BUY_AGENT 分支白名单）：memberId 由上下文注入，证件号不进模型，
 * 返回短期 passengerRef 供 prepareBuy 引用，防止模型构造他人乘车人 id。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PassengerTools {

    private final PassengerApi passengerApi;
    private final ToolRefManager refManager;

    @Tool(name = "listPassengers",
            description = "查询当前用户名下乘车人列表（姓名脱敏），返回 passengerRef 供购票预检引用；只能查询本人乘车人")
    public String listPassengers(
            @ToolParam(required = false, description = "按姓名筛选的关键词") String keyword,
            ToolContext toolContext) {
        ToolExecutionContext ctx = ToolContexts.from(toolContext);
        log.info("[tool] listPassengers userId={} conversationId={} keyword={}",
                ctx.userId(), ctx.conversationId(), keyword);
        String normalized = keyword == null ? null : keyword.trim();
        final String kw = normalized == null || normalized.isEmpty() ? null : normalized;
        List<MemberPassengerDTO> all;
        try {
            all = passengerApi.listByMember(ctx.userId());
        } catch (Exception e) {
            return ToolResult.fail(ToolResult.CODE_UPSTREAM_FAIL, "乘车人查询失败，请稍后重试").render();
        }
        List<Map<String, Object>> items = all.stream()
                .filter(p -> kw == null || (p.getName() != null && p.getName().contains(kw)))
                .map(p -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("passengerRef", refManager.save(ctx.conversationId(), ctx.userId(),
                            ToolConstants.REF_KIND_PASSENGER, Map.of("passengerId", p.getId())));
                    item.put("name", ToolConstants.maskName(p.getName()));
                    item.put("passengerType", p.getPassengerType());
                    item.put("passengerTypeName", ToolConstants.passengerName(p.getPassengerType()));
                    item.put("isSelf", p.getIsSelf());
                    return item;
                })
                .toList();
        if (items.isEmpty()) {
            return ToolResult.ok(List.of(), "名下暂无乘车人，请先在乘车人管理页添加").render();
        }
        return ToolResult.ok(items, "共 " + items.size() + " 名乘车人，购票预检时使用 passengerRef 引用").render();
    }
}
