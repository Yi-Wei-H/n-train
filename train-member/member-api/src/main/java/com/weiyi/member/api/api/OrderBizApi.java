package com.weiyi.member.api.api;

import com.weiyi.common.core.result.PageResult;
import com.weiyi.member.api.dto.MemberOperationResultDTO;
import com.weiyi.member.api.dto.MemberOrderDTO;
import com.weiyi.member.api.dto.MemberRefundResultDTO;
import com.weiyi.member.api.dto.OrderBizCreateCmd;
import com.weiyi.member.api.dto.OrderBizRefundCmd;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 订单用户侧接口（服务间 RPC，AI 工具层消费）：归属校验与订单编排均在 member，
 * 写接口按 idempotencyKey 幂等，并按期望金额复核后执行。
 */
@FeignClient(name = "train-member", contextId = "orderBizApi")
public interface OrderBizApi {

    /** 当前账户订单分页（近期） */
    @GetMapping("/rpc/order/list")
    PageResult<MemberOrderDTO> list(@RequestParam("memberId") long memberId,
                                    @RequestParam(value = "orderStatus", required = false) List<Integer> orderStatus,
                                    @RequestParam(value = "pageNum", defaultValue = "1") long pageNum,
                                    @RequestParam(value = "pageSize", defaultValue = "5") long pageSize);

    /** 订单详情（含明细与可退信息；member 校验归属） */
    @GetMapping("/rpc/order/{orderId}")
    MemberOrderDTO detail(@PathVariable("orderId") long orderId,
                          @RequestParam("memberId") long memberId);

    /** AI 代下单（待支付订单）：幂等 + 期望金额复核 */
    @PostMapping("/rpc/order/create")
    MemberOrderDTO create(@RequestBody OrderBizCreateCmd cmd);

    /** AI 代退票（明细级）：幂等 + 期望金额复核（可计算时） */
    @PostMapping("/rpc/order/refund")
    MemberRefundResultDTO refund(@RequestBody OrderBizRefundCmd cmd);

    /** 按幂等键查询写操作最终结果（崩溃恢复对账用） */
    @GetMapping("/rpc/order/operation-result")
    MemberOperationResultDTO queryOperationResult(@RequestParam("idempotencyKey") String idempotencyKey);
}
