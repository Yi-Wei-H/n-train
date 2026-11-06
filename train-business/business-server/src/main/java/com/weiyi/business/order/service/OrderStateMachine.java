package com.weiyi.business.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.constant.StatusConsts;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.business.order.infra.entity.BOrder;
import com.weiyi.business.order.infra.mapper.BOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 订单轻量状态机：合法转移表 + `WHERE order_status=expectFrom` 乐观锁。
 * 状态变更只准走这里，禁止散落 setOrderStatus（§7）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderStateMachine {

    private final BOrderMapper orderMapper;

    /** 合法转移：from → to 集合 */
    private static final Map<Integer, Set<Integer>> TRANSITIONS = Map.of(
            0, Set.of(1, 4),   // 待支付 → 已支付 / 已取消
            1, Set.of(2, 5),   // 已支付 → 已出行 / 退款中
            2, Set.of(3),      // 已出行 → 已完成
            5, Set.of(6)       // 退款中 → 已关闭
    );

    /**
     * @return true 转移成功；false 当前状态不符（乐观锁未命中）
     */
    public boolean transition(long orderId, int expectFrom, int to) {
        if (!TRANSITIONS.getOrDefault(expectFrom, Set.of()).contains(to)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR,
                    "非法状态转移：" + expectFrom + " → " + to);
        }
        boolean ok = orderMapper.update(null, Wrappers.<BOrder>lambdaUpdate()
                .eq(BOrder::getId, orderId)
                .eq(BOrder::getOrderStatus, expectFrom)
                .set(BOrder::getOrderStatus, to)) == 1;
        if (ok) {
            log.info("订单状态转移：orderId={} {} → {}", orderId, expectFrom, to);
        } else {
            log.warn("订单状态转移未命中（当前状态非 {}）：orderId={} 期望转移到 {}", expectFrom, orderId, to);
        }
        return ok;
    }

    /** 转移成功后补时间字段（payAt/closedAt 等），与转移同一调用事务内执行 */
    public void fillTime(long orderId, LocalDateTime time, boolean pay, boolean close) {
        orderMapper.update(null, Wrappers.<BOrder>lambdaUpdate()
                .eq(BOrder::getId, orderId)
                .set(pay, BOrder::getPayAt, time)
                .set(close, BOrder::getClosedAt, time));
    }

    public void fillCancel(long orderId, LocalDateTime cancelAt, int cancelType) {
        orderMapper.update(null, Wrappers.<BOrder>lambdaUpdate()
                .eq(BOrder::getId, orderId)
                .set(BOrder::getCancelAt, cancelAt)
                .set(BOrder::getCancelType, cancelType));
    }

    /** 断言式转移：失败直接抛 ORDER_STATUS_ERROR */
    public void transitionStrict(long orderId, int expectFrom, int to) {
        if (!transition(orderId, expectFrom, to)) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR);
        }
    }

    /**
     * 批量转移-上车（过站巡检用）：车已发离的上车站集合内，已支付订单批量转已出行。
     * SQL 的 order_status = fromStatus 条件本身就是 CAS，重复巡检幂等，无需 assertLegal。
     *
     * @return 本次推进的订单行数
     */
    public int transitionBoarding(LocalDate rideDate, String trainCode, List<String> departedStations) {
        return orderMapper.markBoarding(rideDate, trainCode, departedStations,
                StatusConsts.ORDER_PAID, StatusConsts.ORDER_TRAVELED);
    }

    /**
     * 批量转移-下车（过站巡检用）：车已到达的下车站集合内，已出行订单批量转已完成。
     *
     * @return 本次推进的订单行数
     */
    public int transitionArrivedDest(LocalDate rideDate, String trainCode, List<String> arrivedStations) {
        return orderMapper.markArrivedDest(rideDate, trainCode, arrivedStations,
                StatusConsts.ORDER_TRAVELED, StatusConsts.ORDER_COMPLETED);
    }
}
