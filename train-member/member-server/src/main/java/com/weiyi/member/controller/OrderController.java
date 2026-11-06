package com.weiyi.member.controller;

import com.weiyi.common.core.result.PageResult;
import com.weiyi.common.core.result.Result;
import com.weiyi.common.redis.ratelimit.LimitDimension;
import com.weiyi.common.redis.ratelimit.RateLimit;
import com.weiyi.business.api.dto.OrderCreateCmd;
import com.weiyi.business.api.dto.OrderDTO;
import com.weiyi.business.api.dto.PayResultDTO;
import com.weiyi.business.api.dto.RefundResultDTO;
import com.weiyi.member.dto.OrderCreateWebCmd;
import com.weiyi.member.dto.RefundWebCmd;
import com.weiyi.member.service.OrderBizService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单：下单（频次触发验证码）、支付、退票、取消、明细、我的订单。
 */
@RestController
@RequestMapping("/order")
public class OrderController {

    private final OrderBizService orderBizService;

    public OrderController(OrderBizService orderBizService) {
        this.orderBizService = orderBizService;
    }

    @PostMapping("/create")
    @RateLimit(dimension = LimitDimension.USER, count = 10, interval = 60, message = "下单太频繁啦，休息一下")
    @RateLimit(dimension = LimitDimension.IP, count = 10, interval = 60, message = "下单太频繁啦，请稍后重试")
    @RateLimit(dimension = LimitDimension.DEVICE, count = 10, interval = 60, message = "下单太频繁啦，请稍后重试")
    public Result<OrderDTO> create(@RequestBody @Valid OrderCreateWebCmd webCmd) {
        OrderCreateCmd cmd = new OrderCreateCmd();
        cmd.setRideDate(webCmd.getRideDate());
        cmd.setTrainCode(webCmd.getTrainCode());
        cmd.setFromStation(webCmd.getFromStation());
        cmd.setToStation(webCmd.getToStation());
        cmd.setSeatType(webCmd.getSeatType());
        cmd.setChosenSeats(webCmd.getChosenSeats());
        return Result.ok(orderBizService.create(cmd, webCmd.getPassengerIds(), webCmd.getCaptchaKey(), webCmd.getCaptchaCode()));
    }

    @PostMapping("/{orderId}/pay")
    public Result<PayResultDTO> pay(@PathVariable long orderId) {
        return Result.ok(orderBizService.pay(orderId));
    }

    @PostMapping("/{orderId}/refund")
    public Result<RefundResultDTO> refund(@PathVariable long orderId,
                                          @RequestBody @Valid RefundWebCmd webCmd) {
        return Result.ok(orderBizService.refund(orderId, webCmd.getItemIds()));
    }

    @PostMapping("/{orderId}/cancel")
    public Result<Void> cancel(@PathVariable long orderId) {
        orderBizService.cancel(orderId);
        return Result.ok();
    }

    @GetMapping("/{orderId}")
    public Result<OrderDTO> detail(@PathVariable long orderId) {
        return Result.ok(orderBizService.detail(orderId));
    }

    @GetMapping("/list")
    public Result<PageResult<OrderDTO>> list(
            @RequestParam(required = false) java.util.List<Integer> orderStatus,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        return Result.ok(orderBizService.myOrders(orderStatus, pageNum, pageSize));
    }
}
