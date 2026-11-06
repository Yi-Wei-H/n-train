package com.weiyi.business.api.api;

import com.weiyi.common.core.result.PageResult;
import com.weiyi.business.api.dto.OrderCreateCmd;
import com.weiyi.business.api.dto.OrderDTO;
import com.weiyi.business.api.dto.OrderQuery;
import com.weiyi.business.api.dto.PayableDTO;
import com.weiyi.business.api.dto.PayResultDTO;
import com.weiyi.business.api.dto.RefundApplyCmd;
import com.weiyi.business.api.dto.RefundBatchDTO;
import com.weiyi.business.api.dto.RefundResultDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 订单域接口（跨服务 saga 编排，钱包在会员域先行、订单域确认）：
 * <ul>
 *   <li>下单：锁座（Feign 同步，失败即回滚）→ 订单落库 → 投递超时关单延迟消息</li>
 *   <li>支付：payable 校验（member）→ 钱包扣款（member 本地事务）→ payConfirm（order）；
 *       确认失败由 member 补偿（冲正入账）</li>
 *   <li>退款：apply（明细转退款中）→ 钱包入账（member）→ confirm（事务消息广播释放座位）；
 *       中断由 member 对账扫描重试 confirm</li>
 *   <li>关单：延迟消息到期触发 / 手动取消，事务消息广播 ORDER_CLOSED 释放座位</li>
 * </ul>
 */
@FeignClient(name = "train-business", contextId = "orderApi")
public interface OrderApi {

    /** 下单（乘车人信息由 member 校验归属后随命令传入） */
    @PostMapping("/od/order/create")
    OrderDTO createOrder(@RequestBody OrderCreateCmd cmd);

    /** 支付前置校验：订单存在、归属正确、待支付；返回应付金额与订单号 */
    @GetMapping("/od/order/{orderId}/payable")
    PayableDTO payable(@PathVariable("orderId") long orderId,
                       @RequestParam("memberId") long memberId);

    /** 支付确认（钱包已扣款后调用）：订单/明细转已支付、生成支付单与车票；幂等（已支付原样返回） */
    @PostMapping("/od/order/{orderId}/pay-confirm")
    PayResultDTO payConfirm(@PathVariable("orderId") long orderId,
                            @RequestParam("memberId") long memberId);

    /** 申请退款（明细转退款中，生成退款批次，不动钱包/座位） */
    @PostMapping("/od/order/refund/apply")
    RefundBatchDTO applyRefund(@RequestBody RefundApplyCmd cmd);

    /** 退款确认（钱包已入账后调用）：明细转已退、车票作废、事务消息广播释放座位；幂等 */
    @PostMapping("/od/order/refund/confirm")
    RefundResultDTO confirmRefund(@RequestParam("batchNo") String batchNo);

    /** 退款批次是否已确认（member 对账扫描用） */
    @GetMapping("/od/order/refund/confirmed")
    boolean refundConfirmed(@RequestParam("batchNo") String batchNo);

    /** 手动取消（仅待支付）：事务消息广播 ORDER_CLOSED */
    @PostMapping("/od/order/{orderId}/cancel")
    boolean cancelIfUnpaid(@PathVariable("orderId") long orderId,
                           @RequestParam("cancelType") int cancelType);

    /** 管理端标记已出行（订单与明细、车票联动） */
    @PostMapping("/od/order/{orderId}/traveled")
    void markTraveled(@PathVariable("orderId") long orderId);

    /** 管理端标记已完成 */
    @PostMapping("/od/order/{orderId}/complete")
    void complete(@PathVariable("orderId") long orderId);

    @GetMapping("/od/order/{orderId}")
    OrderDTO detail(@PathVariable("orderId") long orderId,
                    @RequestParam(value = "memberId", required = false) Long memberId);

    @GetMapping("/od/order/page")
    PageResult<OrderDTO> page(@SpringQueryMap OrderQuery query);

    // ==================== 定时任务触发（train-task 经 Feign 调用） ====================

    /** 关单兜底扫描：超时未支付订单批量关单（延迟消息漏网兜底），返回关单数 */
    @PostMapping("/od/order/sweep/timeout")
    int sweepTimeout();

    /** 过站巡检-上车：车已发离乘客上车站的已支付订单批量转已出行（明细、车票联动），返回推进订单数 */
    @PostMapping("/od/order/sweep/boarding")
    int sweepBoarding();

    /** 过站巡检-下车：车已到达乘客下车站的已出行订单批量转已完成，返回推进订单数 */
    @PostMapping("/od/order/sweep/arrival")
    int sweepArrival();
}
