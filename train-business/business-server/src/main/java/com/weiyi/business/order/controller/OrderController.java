package com.weiyi.business.order.controller;

import com.weiyi.common.core.result.PageResult;
import com.weiyi.business.api.api.OrderApi;
import com.weiyi.business.api.dto.OrderCreateCmd;
import com.weiyi.business.api.dto.OrderDTO;
import com.weiyi.business.api.dto.OrderQuery;
import com.weiyi.business.api.dto.PayableDTO;
import com.weiyi.business.api.dto.PayResultDTO;
import com.weiyi.business.api.dto.RefundApplyCmd;
import com.weiyi.business.api.dto.RefundBatchDTO;
import com.weiyi.business.api.dto.RefundResultDTO;
import com.weiyi.business.order.service.OrderService;
import com.weiyi.business.order.service.TravelSweepService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OrderController implements OrderApi {

    private final OrderService orderService;
    private final TravelSweepService travelSweepService;

    @Override
    public OrderDTO createOrder(OrderCreateCmd cmd) {
        return orderService.createOrder(cmd);
    }

    @Override
    public PayableDTO payable(long orderId, long memberId) {
        return orderService.payable(orderId, memberId);
    }

    @Override
    public PayResultDTO payConfirm(long orderId, long memberId) {
        return orderService.payConfirm(orderId, memberId);
    }

    @Override
    public RefundBatchDTO applyRefund(RefundApplyCmd cmd) {
        return orderService.applyRefund(cmd);
    }

    @Override
    public RefundResultDTO confirmRefund(String batchNo) {
        return orderService.confirmRefund(batchNo);
    }

    @Override
    public boolean refundConfirmed(String batchNo) {
        return orderService.refundConfirmed(batchNo);
    }

    @Override
    public boolean cancelIfUnpaid(long orderId, int cancelType) {
        return orderService.cancelIfUnpaid(orderId, cancelType);
    }

    @Override
    public void markTraveled(long orderId) {
        orderService.markTraveled(orderId);
    }

    @Override
    public void complete(long orderId) {
        orderService.complete(orderId);
    }

    @Override
    public OrderDTO detail(long orderId, Long memberId) {
        return orderService.detail(orderId, memberId);
    }

    @Override
    public PageResult<OrderDTO> page(OrderQuery query) {
        return orderService.page(query);
    }

    @Override
    public int sweepTimeout() {
        return orderService.sweepTimeout();
    }

    @Override
    public int sweepBoarding() {
        return travelSweepService.sweepBoarding();
    }

    @Override
    public int sweepArrival() {
        return travelSweepService.sweepArrival();
    }
}
