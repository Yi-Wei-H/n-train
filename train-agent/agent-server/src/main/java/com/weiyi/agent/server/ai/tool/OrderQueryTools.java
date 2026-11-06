package com.weiyi.agent.server.ai.tool;

import com.weiyi.agent.server.constant.ToolConstants;
import com.weiyi.common.core.result.PageResult;
import com.weiyi.member.api.api.OrderBizApi;
import com.weiyi.member.api.dto.MemberOrderDTO;
import com.weiyi.member.api.dto.MemberOrderItemDTO;
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
 * 订单只读工具（REFUND_AGENT 分支白名单）：只查当前账户近期订单，
 * 返回 orderRef 与可退明细 itemRef 供 prepareRefund 引用；真实订单 id 不进模型。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderQueryTools {

    private final OrderBizApi orderBizApi;
    private final ToolRefManager refManager;

    @Tool(name = "listMyOrders",
            description = "查询当前用户近期订单（含车票明细与可退标记），返回 orderRef 与可退明细 itemRef 供退票预检引用；只能查本人订单")
    public String listMyOrders(
            @ToolParam(required = false, description = "订单状态码筛选") Integer orderStatus,
            @ToolParam(required = false, description = "页码，默认 1") Integer pageNum,
            @ToolParam(required = false, description = "每页条数，默认 5，最大 10") Integer pageSize,
            ToolContext toolContext) {
        ToolExecutionContext ctx = ToolContexts.from(toolContext);
        log.info("[tool] listMyOrders userId={} conversationId={} orderStatus={} pageNum={} pageSize={}",
                ctx.userId(), ctx.conversationId(), orderStatus, pageNum, pageSize);
        long pn = pageNum == null || pageNum < 1 ? 1 : pageNum;
        long ps = pageSize == null || pageSize < 1 ? 5 : Math.min(pageSize, 10);
        PageResult<MemberOrderDTO> page;
        try {
            page = orderBizApi.list(ctx.userId(), orderStatus == null ? null : List.of(orderStatus), pn, ps);
        } catch (Exception e) {
            return ToolResult.fail(ToolResult.CODE_UPSTREAM_FAIL, "订单查询失败，请稍后重试").render();
        }
        List<Map<String, Object>> orders = page.getList().stream().map(order -> {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("orderRef", refManager.save(ctx.conversationId(), ctx.userId(),
                    ToolConstants.REF_KIND_ORDER, Map.of("orderId", order.getOrderId())));
            view.put("orderStatus", order.getOrderStatusName());
            view.put("rideDate", order.getRideDate() == null ? null : order.getRideDate().toString());
            view.put("trainCode", order.getTrainCode());
            view.put("route", order.getFromName() + "→" + order.getToName());
            view.put("totalAmount", order.getTotalAmount());
            view.put("items", order.getItems() == null ? List.of() : order.getItems().stream()
                    .map(item -> itemView(ctx, order.getOrderId(), item))
                    .toList());
            return view;
        }).toList();
        if (orders.isEmpty()) {
            return ToolResult.ok(List.of(), "近期暂无符合条件的订单").render();
        }
        return ToolResult.ok(orders, "共 " + page.getTotal() + " 笔订单，当前第 " + pn + " 页；退票预检使用 orderRef 与可退明细的 itemRef").render();
    }

    /** 明细视图：仅可退明细签发 itemRef，乘车人姓名脱敏 */
    private Map<String, Object> itemView(ToolExecutionContext ctx, Long orderId, MemberOrderItemDTO item) {
        Map<String, Object> view = new LinkedHashMap<>();
        if (Boolean.TRUE.equals(item.getRefundable())) {
            view.put("itemRef", refManager.save(ctx.conversationId(), ctx.userId(),
                    ToolConstants.REF_KIND_ORDER_ITEM,
                    Map.of("orderId", orderId, "orderItemId", item.getOrderItemId())));
        }
        view.put("passengerName", ToolConstants.maskName(item.getPassengerName()));
        view.put("seatTypeName", item.getSeatTypeName());
        view.put("price", item.getPrice());
        view.put("itemStatus", item.getItemStatusName());
        view.put("refundable", item.getRefundable());
        view.put("refundAmount", item.getRefundAmount());
        return view;
    }
}
