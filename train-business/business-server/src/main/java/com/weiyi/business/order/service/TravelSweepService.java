package com.weiyi.business.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiyi.common.core.constant.StatusConsts;
import com.weiyi.business.order.infra.entity.BOrder;
import com.weiyi.business.order.infra.mapper.BOrderItemMapper;
import com.weiyi.business.order.infra.mapper.BOrderMapper;
import com.weiyi.business.order.infra.mapper.BTicketMapper;
import com.weiyi.business.ticket.dto.Route;
import com.weiyi.business.ticket.infra.entity.BDailyTrain;
import com.weiyi.business.ticket.infra.mapper.BDailyTrainMapper;
import com.weiyi.business.ticket.service.RouteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 过站状态巡检（train-task 的 Quartz 周期经 Feign 触发）：按乘客自己的上/下车站推进订单状态。
 * 上车判定 = 车已从该站发车（无检票数据，以发离近似）；下车判定 = 车已到达该站。
 * 站序时刻与跨天折算由列车域 RouteService 统一提供；批量失败重试后单笔兜底，全部条件更新幂等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TravelSweepService {

    private final BDailyTrainMapper dailyTrainMapper;
    private final BOrderMapper orderMapper;
    private final BOrderItemMapper itemMapper;
    private final BTicketMapper ticketMapper;
    private final RouteService routeService;
    private final OrderStateMachine stateMachine;
    private final OrderService orderService;
    private final TransactionTemplate transactionTemplate;

    /** 批量失败后的重试次数（配置可调，总尝试 = 1 + retry） */
    @Value("${ntrain.job.sweep-retry:1}")
    private int sweepRetry;

    /** 一次巡检要处理的车次（运行中/已到达，含历史欠账），站时刻来自共享线路解析 */
    private record DailyRoute(LocalDate rideDate, String trainCode, int status,
                              List<Route.StopTime> stops) {

        /** 车已发离的站（近似乘客已上车） */
        private List<String> departedStations(LocalDateTime now) {
            if (status == StatusConsts.TRAIN_STATUS_ARRIVED) {
                return allCodes();
            }
            return stops.stream()
                    .filter(s -> s.depart() != null
                            && !rideDate.plusDays(s.dayOffset()).atTime(s.depart()).isAfter(now))
                    .map(Route.StopTime::code).toList();
        }

        /** 车已到达的站（乘客已可下车） */
        private List<String> arrivedStations(LocalDateTime now) {
            if (status == StatusConsts.TRAIN_STATUS_ARRIVED) {
                return allCodes();
            }
            return stops.stream()
                    .filter(s -> s.arrive() != null
                            && !rideDate.plusDays(s.dayOffset()).atTime(s.arrive()).isAfter(now))
                    .map(Route.StopTime::code).toList();
        }

        private List<String> allCodes() {
            return stops.stream().map(Route.StopTime::code).toList();
        }
    }

    /** 上车推进：过了乘客上车站发车时刻的已支付订单 → 已出行（明细、车票联动）。返回推进订单数 */
    public int sweepBoarding() {
        LocalDateTime now = LocalDateTime.now();
        int total = 0;
        for (DailyRoute route : loadRoutes()) {
            List<String> departed = route.departedStations(now);
            if (departed.isEmpty()) {
                continue;
            }
            total += runBoarding(route, departed);
        }
        return total;
    }

    /** 下车推进：到了乘客下车站到达时刻的已出行订单 → 已完成。返回推进订单数 */
    public int sweepArrival() {
        LocalDateTime now = LocalDateTime.now();
        int total = 0;
        for (DailyRoute route : loadRoutes()) {
            List<String> arrived = route.arrivedStations(now);
            if (arrived.isEmpty()) {
                continue;
            }
            total += runArrival(route, arrived);
        }
        return total;
    }

    /**
     * 一车次一事务：订单（状态机批量入口）+ 明细 + 车票；失败重试，仍失败单笔兜底
     */
    private int runBoarding(DailyRoute route, List<String> stations) {
        for (int attempt = 0; attempt <= sweepRetry; attempt++) {
            try {
                Integer count = transactionTemplate.execute(status -> {
                    int orders = stateMachine.transitionBoarding(route.rideDate(), route.trainCode(), stations);
                    itemMapper.markTraveledByStations(route.rideDate(), route.trainCode(), stations,
                            StatusConsts.ITEM_PAID, StatusConsts.ITEM_TRAVELED);
                    ticketMapper.markTraveledByStations(route.rideDate(), route.trainCode(), stations,
                            StatusConsts.TICKET_WAIT_RIDE, StatusConsts.TICKET_TRAVELED);
                    return orders;
                });
                if (count != null && count > 0) {
                    log.info("上车推进成功：{} {} 推进订单 {} 笔，发离站 {}", route.rideDate(), route.trainCode(),
                            count, stations);
                }
                return count == null ? 0 : count;
            } catch (Exception e) {
                log.warn("上车推进批量失败 {} {}（第 {} 次尝试）: {}",
                        route.rideDate(), route.trainCode(), attempt + 1, e.getMessage());
            }
        }
        log.warn("上车推进降级单笔兜底 {} {}", route.rideDate(), route.trainCode());
        return fallbackBoarding(route, stations);
    }

    /**
     * 一车次一事务：订单（状态机批量入口）；失败重试，仍失败单笔兜底
     */
    private int runArrival(DailyRoute route, List<String> stations) {
        for (int attempt = 0; attempt <= sweepRetry; attempt++) {
            try {
                Integer count = transactionTemplate.execute(status ->
                        stateMachine.transitionArrivedDest(route.rideDate(), route.trainCode(), stations));
                if (count != null && count > 0) {
                    log.info("下车推进成功：{} {} 推进订单 {} 笔，到达站 {}", route.rideDate(), route.trainCode(),
                            count, stations);
                }
                return count == null ? 0 : count;
            } catch (Exception e) {
                log.warn("下车推进批量失败 {} {}（第 {} 次尝试）: {}",
                        route.rideDate(), route.trainCode(), attempt + 1, e.getMessage());
            }
        }
        log.warn("下车推进降级单笔兜底 {} {}", route.rideDate(), route.trainCode());
        return fallbackArrival(route, stations);
    }

    /** 单笔兜底：逐单走既有事务方法，单笔失败只跳过该单，不阻塞同车其他订单 */
    private int fallbackBoarding(DailyRoute route, List<String> stations) {
        List<BOrder> orders = orderMapper.selectList(Wrappers.<BOrder>lambdaQuery()
                .eq(BOrder::getRideDate, route.rideDate())
                .eq(BOrder::getTrainCode, route.trainCode())
                .in(BOrder::getFromStation, stations)
                .eq(BOrder::getOrderStatus, StatusConsts.ORDER_PAID));
        if (orders.isEmpty()) {
            log.debug("上车推进单笔兜底：无可推进订单：{} {}", route.rideDate(), route.trainCode());
        }
        int done = 0;
        for (BOrder order : orders) {
            try {
                orderService.markTraveled(order.getId());
                done++;
            } catch (Exception e) {
                log.warn("上车推进单笔失败 orderId={}: {}", order.getId(), e.getMessage());
            }
        }
        return done;
    }

    private int fallbackArrival(DailyRoute route, List<String> stations) {
        List<BOrder> orders = orderMapper.selectList(Wrappers.<BOrder>lambdaQuery()
                .eq(BOrder::getRideDate, route.rideDate())
                .eq(BOrder::getTrainCode, route.trainCode())
                .in(BOrder::getToStation, stations)
                .eq(BOrder::getOrderStatus, StatusConsts.ORDER_TRAVELED));
        if (orders.isEmpty()) {
            log.debug("下车推进单笔兜底：无可推进订单：{} {}", route.rideDate(), route.trainCode());
        }
        int done = 0;
        for (BOrder order : orders) {
            try {
                orderService.complete(order.getId());
                done++;
            } catch (Exception e) {
                log.warn("下车推进单笔失败 orderId={}: {}", order.getId(), e.getMessage());
            }
        }
        return done;
    }

    /** 载入运行中/已到达车次及其按序站时刻（同车次模板线路一次解析复用） */
    private List<DailyRoute> loadRoutes() {
        List<BDailyTrain> trains = dailyTrainMapper.selectList(Wrappers.<BDailyTrain>lambdaQuery()
                .in(BDailyTrain::getStatus, StatusConsts.TRAIN_STATUS_RUNNING, StatusConsts.TRAIN_STATUS_ARRIVED));
        if (trains.isEmpty()) {
            log.debug("过站巡检：无运行中/已到达车次");
            return List.of();
        }
        Map<String, Route> routeByCode = new HashMap<>();
        List<DailyRoute> routes = new ArrayList<>(trains.size());
        for (BDailyTrain train : trains) {
            Route route = routeByCode.computeIfAbsent(train.getTrainCode(), routeService::resolve);
            routes.add(new DailyRoute(train.getRideDate(), train.getTrainCode(), train.getStatus(), route.stops()));
        }
        return routes;
    }
}
