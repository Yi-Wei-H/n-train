package com.weiyi.agent.server.ai.tool;

import com.weiyi.agent.server.config.AgentProperties;
import com.weiyi.agent.server.constant.AgentConstants;
import com.weiyi.agent.server.constant.ToolConstants;
import com.weiyi.agent.server.entity.AOperation;
import com.weiyi.agent.server.ai.tool.OperationService;
import com.weiyi.business.api.api.TicketQueryApi;
import com.weiyi.business.api.dto.TicketDTO;
import com.weiyi.common.core.util.JacksonUtil;
import com.weiyi.member.api.api.OrderBizApi;
import com.weiyi.member.api.api.PassengerApi;
import com.weiyi.member.api.dto.MemberOrderDTO;
import com.weiyi.member.api.dto.MemberOrderItemDTO;
import com.weiyi.member.api.dto.MemberPassengerDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 写操作预检工具（BUY_AGENT / REFUND_AGENT 分支白名单）：
 * 实时复检后生成服务端确认摘要并落 PENDING 操作，只做预检不执行任何写动作；
 * 摘要与金额全部由服务端计算，模型输出不参与定价。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PrepareTools {

    private final TicketQueryApi ticketQueryApi;
    private final PassengerApi passengerApi;
    private final OrderBizApi orderBizApi;
    private final ToolRefManager refManager;
    private final WorkMemoryStore memoryStore;
    private final OperationService operationService;
    private final AgentProperties agentProperties;

    @Tool(name = "prepareBuy",
            description = "购票预检：校验所选车票、席别与乘车人，生成订单确认摘要等待用户确认；不会直接下单，必须等用户明确确认")
    public String prepareBuy(
            @ToolParam(description = "当前有效查询列表标识 listToken") String listToken,
            @ToolParam(description = "用户选中的车票引用 ticketRef") String ticketRef,
            @ToolParam(description = "席别代码（车票列表 seats 中的 seatType）") Integer seatType,
            @ToolParam(description = "乘车人引用列表 passengerRefs") List<String> passengerRefs,
            ToolContext toolContext) {
        ToolExecutionContext ctx = ToolContexts.from(toolContext);
        log.info("[tool] prepareBuy userId={} conversationId={} state={} ticketRef={} seatType={} passengerRefs={}",
                ctx.userId(), ctx.conversationId(), ctx.state(), ticketRef, seatType, passengerRefs);
        if (!"BUY_AGENT".equals(ctx.state())) {
            return ToolResult.fail(ToolResult.CODE_STATE_INVALID, "当前不在购票流程中，不能发起下单预检").render();
        }
        if (ticketRef == null || seatType == null || passengerRefs == null || passengerRefs.isEmpty()) {
            return ToolResult.fail(ToolResult.CODE_PARAM_INVALID, "车票引用、席别与乘车人引用均不能为空").render();
        }
        if (refManager.resolve(ctx.conversationId(), ctx.userId(), listToken, ToolConstants.REF_KIND_LIST) == null) {
            return ToolResult.fail(ToolResult.CODE_REF_INVALID, "查询列表已过期，请重新查询车票").render();
        }
        Map<String, Object> ticket = refManager.resolve(ctx.conversationId(), ctx.userId(),
                ticketRef, ToolConstants.REF_KIND_TICKET);
        if (ticket == null) {
            return ToolResult.fail(ToolResult.CODE_REF_INVALID, "车票引用无效或已过期，请重新查询车票").render();
        }
        List<Long> passengerIds = new ArrayList<>();
        for (String ref : passengerRefs) {
            Map<String, Object> payload = refManager.resolve(ctx.conversationId(), ctx.userId(),
                    ref, ToolConstants.REF_KIND_PASSENGER);
            if (payload == null) {
                return ToolResult.fail(ToolResult.CODE_REF_INVALID, "乘车人引用无效或已过期，请重新获取乘车人列表").render();
            }
            passengerIds.add((Long) payload.get("passengerId"));
        }

        // 实时复检余票与价格（预检不锁库存，执行时业务侧仍做最终校验）
        LocalDate rideDate = LocalDate.parse((String) ticket.get("rideDate"));
        List<TicketDTO> seatRows;
        try {
            seatRows = ticketQueryApi.queryTicketDetail(rideDate, (String) ticket.get("trainCode"),
                    (String) ticket.get("fromStation"), (String) ticket.get("toStation"));
        } catch (Exception e) {
            return ToolResult.fail(ToolResult.CODE_UPSTREAM_FAIL, "余票复核失败，请稍后重试").render();
        }
        TicketDTO seatRow = seatRows.stream()
                .filter(row -> seatType.equals(row.getSeatType()))
                .findFirst()
                .orElse(null);
        int count = passengerIds.size();
        if (seatRow == null || seatRow.getRemaining() == null || seatRow.getRemaining() < count) {
            return ToolResult.fail(ToolResult.CODE_STOCK_CHANGED,
                    seatRow == null ? "该车次无该席别，请重新选择" : "余票不足（剩余 " + seatRow.getRemaining() + " 张）").render();
        }
        BigDecimal amount = seatRow.getPrice() == null ? null : seatRow.getPrice().multiply(BigDecimal.valueOf(count));

        // 乘车人归属复核与姓名脱敏
        List<MemberPassengerDTO> passengers;
        try {
            passengers = passengerApi.listByMember(ctx.userId());
        } catch (Exception e) {
            return ToolResult.fail(ToolResult.CODE_UPSTREAM_FAIL, "乘车人信息获取失败，请稍后重试").render();
        }
        List<String> maskedNames = new ArrayList<>();
        for (Long passengerId : passengerIds) {
            MemberPassengerDTO matched = passengers.stream()
                    .filter(p -> passengerId.equals(p.getId()))
                    .findFirst()
                    .orElse(null);
            if (matched == null) {
                return ToolResult.fail(ToolResult.CODE_REF_INVALID, "乘车人已变化，请重新获取乘车人列表").render();
            }
            maskedNames.add(ToolConstants.maskName(matched.getName()));
        }

        String seatTypeName = ToolConstants.seatName(seatType);
        String summary = "【购票确认】\n"
                + "车次：" + ticket.get("trainCode") + "  " + rideDate + "  " + ticket.get("fromName") + " → " + ticket.get("toName") + "\n"
                + "席别：" + seatTypeName + "\n"
                + "乘车人：" + String.join("、", maskedNames) + "（共 " + count + " 人）\n"
                + "应付总额：" + amount + " 元\n"
                + "请在下方确认卡片中点击「确认」才会提交订单（待支付），下单后仍需在订单页完成支付。";

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("listToken", listToken);
        snapshot.put("ticketRef", ticketRef);
        snapshot.put("trainCode", ticket.get("trainCode"));
        snapshot.put("fromStation", ticket.get("fromStation"));
        snapshot.put("toStation", ticket.get("toStation"));
        snapshot.put("fromName", ticket.get("fromName"));
        snapshot.put("toName", ticket.get("toName"));
        snapshot.put("rideDate", rideDate.toString());
        snapshot.put("seatType", seatType);
        snapshot.put("passengerIds", passengerIds);
        snapshot.put("passengerCount", count);
        snapshot.put("expectedAmount", amount);

        AOperation operation = operationService.createPending(ctx.userId(), ctx.conversationId(),
                AgentConstants.OPERATION_TYPE_BUY, summary, JacksonUtil.toJson(snapshot));
        mirror(operation, summary);

        // 模型可见数据不携带 confirmId/operationId：确认动作走卡片按钮 → /ai/order，服务端按身份反查
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summaryVersion", operation.getSummaryVersion());
        data.put("expiresAt", operation.getExpiresAt().toString());
        data.put("type", "BUY");
        return ToolResult.ok(data, summary).render();
    }

    @Tool(name = "prepareRefund",
            description = "退票预检：校验订单归属与可退状态，生成退票确认摘要等待用户确认；不会直接退款，必须等用户明确确认")
    public String prepareRefund(
            @ToolParam(description = "订单引用 orderRef") String orderRef,
            @ToolParam(description = "要退的票明细引用列表 itemRefs") List<String> itemRefs,
            ToolContext toolContext) {
        ToolExecutionContext ctx = ToolContexts.from(toolContext);
        log.info("[tool] prepareRefund userId={} conversationId={} state={} orderRef={} itemRefs={}",
                ctx.userId(), ctx.conversationId(), ctx.state(), orderRef, itemRefs);
        if (!"REFUND_AGENT".equals(ctx.state())) {
            return ToolResult.fail(ToolResult.CODE_STATE_INVALID, "当前不在退票流程中，不能发起退票预检").render();
        }
        if (orderRef == null || itemRefs == null || itemRefs.isEmpty()) {
            return ToolResult.fail(ToolResult.CODE_PARAM_INVALID, "订单引用与明细引用不能为空").render();
        }
        Map<String, Object> orderPayload = refManager.resolve(ctx.conversationId(), ctx.userId(),
                orderRef, ToolConstants.REF_KIND_ORDER);
        if (orderPayload == null) {
            return ToolResult.fail(ToolResult.CODE_REF_INVALID, "订单引用无效或已过期，请重新获取订单列表").render();
        }
        Long orderId = (Long) orderPayload.get("orderId");
        List<Long> itemIds = new ArrayList<>();
        for (String ref : itemRefs) {
            Map<String, Object> payload = refManager.resolve(ctx.conversationId(), ctx.userId(),
                    ref, ToolConstants.REF_KIND_ORDER_ITEM);
            if (payload == null || !orderId.equals(payload.get("orderId"))) {
                return ToolResult.fail(ToolResult.CODE_REF_INVALID, "票明细引用无效或不属于该订单，请重新获取订单列表").render();
            }
            itemIds.add((Long) payload.get("orderItemId"));
        }

        // 以最新订单详情为准，不吃旧列表快照
        MemberOrderDTO detail;
        try {
            detail = orderBizApi.detail(orderId, ctx.userId());
        } catch (Exception e) {
            return ToolResult.fail(ToolResult.CODE_UPSTREAM_FAIL,
                    e.getMessage() != null ? e.getMessage() : "订单查询失败，请稍后重试").render();
        }
        List<MemberOrderItemDTO> refundItems = new ArrayList<>();
        BigDecimal refundAmount = BigDecimal.ZERO;
        for (Long itemId : itemIds) {
            MemberOrderItemDTO matched = detail.getItems() == null ? null : detail.getItems().stream()
                    .filter(item -> itemId.equals(item.getOrderItemId()))
                    .findFirst()
                    .orElse(null);
            if (matched == null || !Boolean.TRUE.equals(matched.getRefundable())) {
                return ToolResult.fail(ToolResult.CODE_STOCK_CHANGED, "所选车票当前不可退（状态已变化），请重新确认订单").render();
            }
            refundItems.add(matched);
            if (matched.getRefundAmount() != null) {
                refundAmount = refundAmount.add(matched.getRefundAmount());
            }
        }
        boolean amountKnown = refundItems.stream().allMatch(item -> item.getRefundAmount() != null);

        List<String> maskedNames = refundItems.stream()
                .map(item -> ToolConstants.maskName(item.getPassengerName()) + "(" + item.getSeatTypeName() + ")")
                .toList();
        String summary = "【退票确认】\n"
                + "订单：" + ToolConstants.maskOrderNo(detail.getOrderNo())
                + "  " + detail.getTrainCode() + "  " + detail.getRideDate() + "\n"
                + "退票对象：" + String.join("、", maskedNames) + "（共 " + refundItems.size() + " 张）\n"
                + (amountKnown ? "应退金额：" + refundAmount + " 元\n" : "应退金额以实际退款为准\n")
                + "退票不可逆，请在下方确认卡片中点击「确认」才会提交退款申请，款项将退回钱包。";

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("orderId", orderId);
        snapshot.put("orderNo", detail.getOrderNo());
        snapshot.put("itemIds", itemIds);
        if (amountKnown) {
            snapshot.put("expectedRefundAmount", refundAmount);
        }

        AOperation operation = operationService.createPending(ctx.userId(), ctx.conversationId(),
                AgentConstants.OPERATION_TYPE_REFUND, summary, JacksonUtil.toJson(snapshot));
        mirror(operation, summary);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summaryVersion", operation.getSummaryVersion());
        data.put("expiresAt", operation.getExpiresAt().toString());
        data.put("type", "REFUND");
        return ToolResult.ok(data, summary).render();
    }

    /** 待确认单镜像写工作记忆（权威在 a_ai_operation），分支转 WAIT_CONFIRM */
    private void mirror(AOperation operation, String summary) {
        WorkMemory.PendingConfirm pending = new WorkMemory.PendingConfirm();
        pending.setOperationId(operation.getId());
        pending.setConfirmId(operation.getConfirmId());
        pending.setType(operation.getType());
        pending.setSummaryVersion(operation.getSummaryVersion());
        pending.setSummary(summary);
        pending.setExpiresAt(operation.getExpiresAt().toString());
        memoryStore.setPendingConfirm(operation.getConversationId(), pending);
        memoryStore.updateAgentSub(operation.getConversationId(), "WAIT_CONFIRM");
    }
}
