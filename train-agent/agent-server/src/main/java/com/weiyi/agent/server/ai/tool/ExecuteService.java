package com.weiyi.agent.server.ai.tool;

import com.weiyi.agent.server.entity.AOperation;
import com.weiyi.agent.server.ai.tool.OperationService;
import com.weiyi.business.api.api.TicketQueryApi;
import com.weiyi.business.api.dto.TicketDTO;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.util.JacksonUtil;
import com.weiyi.member.api.api.OrderBizApi;
import com.weiyi.member.api.api.PassengerApi;
import com.weiyi.member.api.dto.MemberOrderDTO;
import com.weiyi.member.api.dto.MemberOrderItemDTO;
import com.weiyi.member.api.dto.MemberPassengerDTO;
import com.weiyi.member.api.dto.MemberRefundResultDTO;
import com.weiyi.member.api.dto.OrderBizCreateCmd;
import com.weiyi.member.api.dto.OrderBizRefundCmd;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 写操作执行（服务端内部能力，不注册 @Tool、模型不可见）：
 * 执行前复检确认快照 → CAS 一次性消费确认令牌 → 以 operationId 为幂等键调用 member 写接口。
 * 结果不明置 UNKNOWN 交对账，绝不盲目重试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecuteService {

    /** 执行租约：调用 member 期间的崩溃回收窗口（恢复任务后续接入） */
    private static final Duration EXECUTION_LEASE = Duration.ofSeconds(60);

    private final TicketQueryApi ticketQueryApi;
    private final PassengerApi passengerApi;
    private final OrderBizApi orderBizApi;
    private final OperationService operationService;
    private final WorkMemoryStore memoryStore;

    /** 购票执行：仅供确认链路调用 */
    public ToolResult executeBuy(Long operationId) {
        AOperation operation = operationService.loadActivePending(operationId);
        if (operation == null) {
            return ToolResult.fail(ToolResult.CODE_STATE_INVALID, "确认已失效或已被处理，请重新发起购票");
        }
        BuySnapshot snapshot = JacksonUtil.fromJson(operation.getSummarySnapshot(), BuySnapshot.class);
        LocalDate rideDate = LocalDate.parse(snapshot.getRideDate());

        // 执行前复检：余票、价格与确认快照一致才执行，任何影响权益的变化作废重来
        List<TicketDTO> seatRows;
        try {
            seatRows = ticketQueryApi.queryTicketDetail(rideDate, snapshot.getTrainCode(),
                    snapshot.getFromStation(), snapshot.getToStation());
        } catch (Exception e) {
            return ToolResult.fail(ToolResult.CODE_UPSTREAM_FAIL, "余票复核失败，本次未下单");
        }
        TicketDTO seatRow = seatRows.stream()
                .filter(row -> snapshot.getSeatType().equals(row.getSeatType()))
                .findFirst()
                .orElse(null);
        int count = snapshot.getPassengerIds().size();
        if (seatRow == null || seatRow.getRemaining() == null || seatRow.getRemaining() < count
                || seatRow.getPrice() == null || snapshot.getExpectedAmount() == null
                || seatRow.getPrice().compareTo(snapshot.getExpectedAmount()) != 0) {
            operationService.cancel(operationId, ToolResult.CODE_STOCK_CHANGED);
            return ToolResult.fail(ToolResult.CODE_STOCK_CHANGED, "车票价格或余票已变化，本次未下单，请重新查询并确认");
        }
        try {
            List<Long> existing = passengerApi.listByMember(operation.getUserId()).stream()
                    .map(MemberPassengerDTO::getId)
                    .toList();
            if (!existing.containsAll(snapshot.getPassengerIds())) {
                operationService.cancel(operationId, ToolResult.CODE_REF_INVALID);
                return ToolResult.fail(ToolResult.CODE_REF_INVALID, "乘车人已变化，本次未下单，请重新发起");
            }
        } catch (BusinessException e) {
            return bizFail(operationId, e, "下单");
        } catch (Exception e) {
            return ToolResult.fail(ToolResult.CODE_UPSTREAM_FAIL, "乘车人复核失败，本次未下单");
        }

        if (!operationService.casToExecuting(operationId, EXECUTION_LEASE)) {
            return ToolResult.fail(ToolResult.CODE_STATE_INVALID, "该确认已被处理");
        }
        OrderBizCreateCmd cmd = new OrderBizCreateCmd();
        cmd.setMemberId(operation.getUserId());
        cmd.setRideDate(rideDate);
        cmd.setTrainCode(snapshot.getTrainCode());
        cmd.setFromStation(snapshot.getFromStation());
        cmd.setToStation(snapshot.getToStation());
        cmd.setSeatType(snapshot.getSeatType());
        cmd.setPassengerIds(snapshot.getPassengerIds());
        cmd.setIdempotencyKey(operation.getIdempotencyKey());
        cmd.setExpectedAmount(snapshot.getExpectedAmount());
        try {
            MemberOrderDTO order = orderBizApi.create(cmd);
            finish(operation.getConversationId(), operationId, order == null ? null : order.getOrderNo());
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            result.put("orderNo", order == null ? null : order.getOrderNo());
            if (order != null && order.getPayDeadline() != null) {
                result.put("payDeadline", order.getPayDeadline().toString());
            }
            return ToolResult.ok(result, "下单成功（待支付），订单号 " + (order == null ? "" : order.getOrderNo())
                    + "，请在订单页完成支付");
        } catch (BusinessException e) {
            operationService.markFailed(operationId, "BIZ_FAIL");
            String msg = e.getMessage() != null ? e.getMessage() : "下单失败，本次未成交";
            return ToolResult.fail(ToolResult.CODE_UPSTREAM_FAIL, msg);
        } catch (Exception e) {
            log.warn("AI 代下单结果不明：operationId={}, conversationId={}", operationId, operation.getConversationId(), e);
            operationService.markUnknown(operationId);
            return ToolResult.fail(ToolResult.CODE_RESULT_UNKNOWN, "下单结果确认中，请稍后在订单页查看，请勿重复提交");
        }
    }

    /** 退票执行：仅供确认链路调用 */
    public ToolResult executeRefund(Long operationId) {
        AOperation operation = operationService.loadActivePending(operationId);
        if (operation == null) {
            return ToolResult.fail(ToolResult.CODE_STATE_INVALID, "确认已失效或已被处理，请重新发起退票");
        }
        RefundSnapshot snapshot = JacksonUtil.fromJson(operation.getSummarySnapshot(), RefundSnapshot.class);

        // 执行前复检：以最新订单详情为准，明细仍可退、金额与快照一致才执行
        MemberOrderDTO detail;
        try {
            detail = orderBizApi.detail(snapshot.getOrderId(), operation.getUserId());
        } catch (BusinessException e) {
            return bizFail(operationId, e, "退票");
        } catch (Exception e) {
            return ToolResult.fail(ToolResult.CODE_UPSTREAM_FAIL, "订单复核失败，本次未退票");
        }
        BigDecimal liveAmount = BigDecimal.ZERO;
        for (Long itemId : snapshot.getItemIds()) {
            MemberOrderItemDTO item = detail.getItems() == null ? null : detail.getItems().stream()
                    .filter(i -> itemId.equals(i.getOrderItemId()))
                    .findFirst()
                    .orElse(null);
            if (item == null || !Boolean.TRUE.equals(item.getRefundable())) {
                operationService.cancel(operationId, ToolResult.CODE_STOCK_CHANGED);
                return ToolResult.fail(ToolResult.CODE_STOCK_CHANGED, "车票状态已变化不可退，本次未退票，请重新确认订单");
            }
            if (item.getRefundAmount() != null) {
                liveAmount = liveAmount.add(item.getRefundAmount());
            }
        }
        if (snapshot.getExpectedRefundAmount() != null
                && snapshot.getExpectedRefundAmount().compareTo(liveAmount) != 0) {
            operationService.cancel(operationId, ToolResult.CODE_STOCK_CHANGED);
            return ToolResult.fail(ToolResult.CODE_STOCK_CHANGED, "应退金额已变化，本次未退票，请重新确认");
        }

        if (!operationService.casToExecuting(operationId, EXECUTION_LEASE)) {
            return ToolResult.fail(ToolResult.CODE_STATE_INVALID, "该确认已被处理");
        }
        OrderBizRefundCmd cmd = new OrderBizRefundCmd();
        cmd.setMemberId(operation.getUserId());
        cmd.setOrderId(snapshot.getOrderId());
        cmd.setItemIds(snapshot.getItemIds());
        cmd.setIdempotencyKey(operation.getIdempotencyKey());
        cmd.setExpectedRefundAmount(snapshot.getExpectedRefundAmount());
        try {
            MemberRefundResultDTO refund = orderBizApi.refund(cmd);
            String batchNo = refund == null ? null : refund.getBatchNo();
            finish(operation.getConversationId(), operationId, batchNo);
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            result.put("batchNo", batchNo);
            result.put("refundAmount", refund == null ? null : refund.getRefundAmount());
            return ToolResult.ok(result, "退票成功，退款批次 " + batchNo + "，应退金额 "
                    + (refund == null || refund.getRefundAmount() == null ? "以实际为准" : refund.getRefundAmount() + " 元")
                    + "，将退回钱包");
        } catch (BusinessException e) {
            operationService.markFailed(operationId, "BIZ_FAIL");
            String msg = e.getMessage() != null ? e.getMessage() : "退票失败，本次未退票";
            return ToolResult.fail(ToolResult.CODE_UPSTREAM_FAIL, msg);
        } catch (Exception e) {
            log.warn("AI 代退票结果不明：operationId={}, conversationId={}", operationId, operation.getConversationId(), e);
            operationService.markUnknown(operationId);
            return ToolResult.fail(ToolResult.CODE_RESULT_UNKNOWN, "退票结果确认中，请稍后在订单页查看，请勿重复提交");
        }
    }

    /** 成功收尾：落业务单号、清待确认镜像、分支子状态复位 */
    private void finish(Long conversationId, Long operationId, String bizNo) {
        operationService.markSuccess(operationId, bizNo);
        memoryStore.setPendingConfirm(conversationId, null);
        memoryStore.updateAgentSub(conversationId, null);
    }

    /** 复检阶段上游业务拒绝：不改写确认状态（仍 PENDING 可重新预检覆盖），原文案透出 */
    private ToolResult bizFail(Long operationId, BusinessException e, String action) {
        String msg = e.getMessage() != null ? e.getMessage() : (action + "校验未通过");
        return ToolResult.fail(ToolResult.CODE_UPSTREAM_FAIL, msg);
    }

    /** 购票确认快照（预检写入，执行复检的比对基准） */
    @Data
    public static class BuySnapshot {
        private String trainCode;
        private String fromStation;
        private String toStation;
        private String rideDate;
        private Integer seatType;
        private List<Long> passengerIds;
        private BigDecimal expectedAmount;
    }

    /** 退票确认快照 */
    @Data
    public static class RefundSnapshot {
        private Long orderId;
        private String orderNo;
        private List<Long> itemIds;
        private BigDecimal expectedRefundAmount;
    }
}
