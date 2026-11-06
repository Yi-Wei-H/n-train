package com.weiyi.business.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiyi.common.core.constant.StatusConsts;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.result.PageResult;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.common.core.util.IdUtil;
import com.weiyi.common.mq.core.MqConst;
import com.weiyi.common.mq.core.MqSender;
import com.weiyi.common.mq.core.TransactionalAction;
import com.weiyi.common.mq.event.OrderClosedEvent;
import com.weiyi.common.mq.event.OrderRefundedEvent;
import com.weiyi.common.mq.event.OrderTimeoutEvent;
import com.weiyi.common.mybatis.util.PageUtils;
import com.weiyi.business.api.dto.OrderCreateCmd;
import com.weiyi.business.api.dto.OrderDTO;
import com.weiyi.business.api.dto.OrderItemDTO;
import com.weiyi.business.api.dto.OrderPassengerCmd;
import com.weiyi.business.api.dto.OrderQuery;
import com.weiyi.business.api.dto.PayableDTO;
import com.weiyi.business.api.dto.PayResultDTO;
import com.weiyi.business.api.dto.RefundApplyCmd;
import com.weiyi.business.api.dto.RefundBatchDTO;
import com.weiyi.business.api.dto.RefundResultDTO;
import com.weiyi.business.api.dto.SeatDTO;
import com.weiyi.business.order.config.OrderProperties;
import com.weiyi.business.order.infra.entity.BOrder;
import com.weiyi.business.order.infra.entity.BOrderItem;
import com.weiyi.business.order.infra.entity.BPayment;
import com.weiyi.business.order.infra.entity.BRefundBatch;
import com.weiyi.business.order.infra.entity.BTicket;
import com.weiyi.business.order.infra.mapper.BOrderItemMapper;
import com.weiyi.business.order.infra.mapper.BOrderMapper;
import com.weiyi.business.order.infra.mapper.BPaymentMapper;
import com.weiyi.business.order.infra.mapper.BRefundBatchMapper;
import com.weiyi.business.order.infra.mapper.BTicketMapper;
import com.weiyi.business.ticket.dto.Route;
import com.weiyi.business.ticket.dto.SeatLockCmd;
import com.weiyi.business.ticket.dto.SeatLockResult;
import com.weiyi.business.ticket.service.RouteService;
import com.weiyi.business.ticket.service.SeatLockService;
import com.weiyi.business.ticket.service.SkTokenService;
import com.weiyi.common.redis.constant.RedisKeys;
import com.weiyi.common.redis.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataAccessException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 订单域聚合（座位在列车域、同库本地事务；钱包在会员域）：
 * <ul>
 *   <li>下单：令牌大闸准入（Redis 计数，宁松勿紧）→ 锁座（扣余票+位图占座+占用记录）
 *       与订单落库同一本地事务，外层 Redis 锁"日期+车次"串行化位图读写且覆盖到事务提交之后；
 *       成功后投递超时关单延迟消息</li>
 *   <li>支付：member 先扣钱包再调 payConfirm；payConfirm 幂等（已支付原样返回），
 *       确认失败的冲正由 member 完成</li>
 *   <li>退款：apply（明细转退款中）→ member 入账 → confirm（本地事务含列车域释放座位 +
 *       事务消息广播 ORDER_REFUNDED）；中断由 member 对账扫描重试 confirm</li>
 *   <li>关单：超时延迟消息/手动触发，本地取消 + 释放座位同一事务 + 事务消息广播
 *       ORDER_CLOSED（会员域失效余票缓存）</li>
 * </ul>
 * 支付等待窗口不处于任何打开的事务中：超时关单由延迟消息触发 + train-task 兜底扫描，
 * 与 Seata 类全局事务的自动回滚超时天然解耦。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final BOrderMapper orderMapper;
    private final BOrderItemMapper itemMapper;
    private final BPaymentMapper paymentMapper;
    private final BTicketMapper ticketMapper;
    private final BRefundBatchMapper refundBatchMapper;
    private final OrderStateMachine stateMachine;
    private final StationNameResolver stationNameResolver;
    private final SeatLockService seatLockService;
    private final RouteService routeService;
    private final SkTokenService skTokenService;
    private final RedisService redisService;
    private final MqSender mqSender;
    private final TransactionTemplate transactionTemplate;
    private final OrderProperties properties;

    // ==================== 下单 ====================

    /**
     * 下单：令牌大闸（重试块外，只扣一次）→ 幂等键只生成一次（orderNo/itemIds）
     * → 重试单元 = "Redis 车次锁 + 本地事务 + 延迟消息投递"整块，同一请求串行重试且携带同一 orderNo。
     * 仅瞬态异常重试（DataAccessException / 锁竞争）；BusinessException 首次失败即上抛；
     * 重试前按 orderNo 回查 b_order，首试已提交则查库组装返回同一订单，不再进入事务。
     */
    public OrderDTO createOrder(OrderCreateCmd cmd) {
        // ⓪ 令牌大闸：按"日期+车次"准入，把洪峰挡在锁与事务之前
        skTokenService.tryAcquire(cmd.getRideDate(), cmd.getTrainCode());

        String orderNo = IdUtil.nextNo("O");
        List<OrderPassengerCmd> passengers = cmd.getPassengers();
        log.info("下单开始：orderNo={} member={} {} {} {} → {} 席别={} 人数={} 自选座={}",
                orderNo, cmd.getMemberId(), cmd.getRideDate(), cmd.getTrainCode(),
                cmd.getFromStation(), cmd.getToStation(), cmd.getSeatType(),
                passengers.size(), cmd.getChosenSeats());
        // 明细 ID 预生成：锁座占用记录与订单明细一一对应，退款按明细释放
        List<Long> itemIds = passengers.stream().map(p -> IdUtil.nextId()).toList();

        SeatLockCmd lockCmd = new SeatLockCmd();
        lockCmd.setOrderNo(orderNo);
        lockCmd.setOrderItemIds(itemIds);
        lockCmd.setRideDate(cmd.getRideDate());
        lockCmd.setTrainCode(cmd.getTrainCode());
        lockCmd.setFromStation(cmd.getFromStation());
        lockCmd.setToStation(cmd.getToStation());
        lockCmd.setSeatType(cmd.getSeatType());
        lockCmd.setCount(passengers.size());
        lockCmd.setChosenSeats(cmd.getChosenSeats());

        // ① 锁座 + 订单落库 = 同一本地事务；Redis 锁包裹事务（提交后才放锁，避免位图脏读）
        String lockKey = RedisKeys.orderLock(cmd.getRideDate().toString(), cmd.getTrainCode());
        long deadline = System.currentTimeMillis() + properties.getCreateRetryBudget().toMillis();
        int maxAttempts = properties.getCreateMaxAttempts();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            if (attempt > 1) {
                if (!awaitBackoff(deadline)) {
                    break;
                }
                OrderDTO replayed = replayIfCommitted(orderNo);
                if (replayed != null) {
                    log.info("下单重试命中已提交订单，返回同一订单（未重复落库/占座/扣令牌）：orderNo={} 第 {}/{} 次尝试",
                            orderNo, attempt, maxAttempts);
                    return replayed;
                }
            }
            // 锁等待 = min(剩余预算, 上限)，预算耗尽不再进入尝试
            long lockWait = Math.min(deadline - System.currentTimeMillis(),
                    properties.getCreateLockWaitMax().toMillis());
            if (lockWait <= 0) {
                break;
            }
            if (!redisService.tryLock(lockKey, lockWait, 15000)) {
                log.warn("下单第 {}/{} 次尝试未抢到车次锁：orderNo={}", attempt, maxAttempts, orderNo);
                continue;
            }
            try {
                Persisted persisted = transactionTemplate.execute(status -> {
                    skTokenService.consumeInTx(cmd.getRideDate(), cmd.getTrainCode());
                    SeatLockResult lock = seatLockService.lockInTx(lockCmd);
                    return persistOrder(cmd, orderNo, itemIds, lock);
                });
                // ② 事务提交后投递超时关单延迟消息：取覆盖支付时限的最小档位（消息丢失由 task 兜底扫描覆盖）
                OrderTimeoutEvent timeout = new OrderTimeoutEvent();
                timeout.setOrderId(persisted.order().getId());
                mqSender.sendDelayQuietly(MqConst.TOPIC_ORDER_TIMEOUT, null, orderNo, timeout,
                        MqConst.delayLevelCovering(properties.payTimeout()));
                OrderDTO dto = toDTO(persisted.order(), persisted.items(), persisted.seatDegraded());
                log.info("下单成功：orderNo={} orderId={} 金额={} 降级选座={} 明细数={} 第 {}/{} 次尝试",
                        orderNo, persisted.order().getId(), persisted.order().getTotalAmount(),
                        persisted.seatDegraded(), persisted.items().size(), attempt, maxAttempts);
                return dto;
            } catch (BusinessException e) {
                // 余票不足/令牌耗尽/席别未开售等：不可重试，首次失败即上抛
                throw e;
            } catch (DataAccessException e) {
                log.warn("下单第 {}/{} 次尝试事务失败（瞬态，整体重放）：orderNo={} {}",
                        attempt, maxAttempts, orderNo, e.getMessage());
            } finally {
                redisService.unlock(lockKey);
            }
        }
        log.warn("下单重试预算耗尽或车次锁始终未抢到：orderNo={} 总尝试上限 {} 次 预算 {}ms",
                orderNo, maxAttempts, properties.getCreateRetryBudget().toMillis());
        throw new BusinessException(ErrorCode.DATA_CONFLICT, "当前购票人数过多，请重试");
    }

    /** 重试前退避 50~100ms 随机；预算已耗尽返回 false（由调用方终止重试） */
    private boolean awaitBackoff(long deadline) {
        long remaining = deadline - System.currentTimeMillis();
        if (remaining <= 0) {
            return false;
        }
        long backoff = Math.min(50 + ThreadLocalRandom.current().nextLong(51), remaining);
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return deadline - System.currentTimeMillis() > 0;
    }

    /** 幂等回查：首试事务已提交则查库组装返回同一订单（seatDegraded 无历史，恒 null），不再进入事务 */
    private OrderDTO replayIfCommitted(String orderNo) {
        BOrder existing = orderMapper.selectOne(Wrappers.<BOrder>lambdaQuery()
                .eq(BOrder::getOrderNo, orderNo));
        if (existing == null) {
            return null;
        }
        List<BOrderItem> items = itemMapper.selectList(Wrappers.<BOrderItem>lambdaQuery()
                .eq(BOrderItem::getOrderId, existing.getId()));
        return toDTO(existing, items, null);
    }

    private Persisted persistOrder(OrderCreateCmd cmd, String orderNo, List<Long> itemIds, SeatLockResult lock) {
        List<SeatDTO> seats = lock.getSeats();
        BigDecimal unitPrice = lock.getUnitPrice();
        BizAssert.isTrue(seats != null && seats.size() == itemIds.size() && unitPrice != null,
                ErrorCode.SYSTEM_ERROR, "锁座结果不完整");

        BOrder order = new BOrder();
        order.setOrderNo(orderNo);
        order.setMemberId(cmd.getMemberId());
        order.setRideDate(cmd.getRideDate());
        order.setTrainCode(cmd.getTrainCode());
        order.setFromStation(cmd.getFromStation());
        order.setToStation(cmd.getToStation());
        order.setDepartureTime(lock.getDepartureTime());
        order.setArrivalTime(lock.getArrivalTime());
        order.setTotalAmount(unitPrice.multiply(BigDecimal.valueOf(itemIds.size())));
        order.setOrderStatus(StatusConsts.ORDER_WAIT_PAY);
        orderMapper.insert(order);

        // 明细批量 INSERT（多值单语句一次往返）；自定义 SQL 不走 MP 自动填充，时间戳在此预生成
        LocalDateTime now = LocalDateTime.now();
        List<BOrderItem> items = new ArrayList<>(itemIds.size());
        for (int i = 0; i < itemIds.size(); i++) {
            OrderPassengerCmd passenger = cmd.getPassengers().get(i);
            SeatDTO seat = seats.get(i);
            BOrderItem item = new BOrderItem();
            item.setId(itemIds.get(i));
            item.setOrderId(order.getId());
            item.setMemberId(cmd.getMemberId());
            item.setPassengerId(passenger.getPassengerId());
            item.setPassengerName(passenger.getName());
            item.setIdCardNo(passenger.getIdCardNo());
            item.setTicketType(passenger.getTicketType());
            item.setSeatType(cmd.getSeatType());
            item.setCarriageNo(seat.getCarriageNo());
            item.setSeatNo(seat.getSeatNo());
            item.setFromStation(cmd.getFromStation());
            item.setToStation(cmd.getToStation());
            item.setPrice(unitPrice);
            item.setItemStatus(StatusConsts.ITEM_WAIT_PAY);
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            items.add(item);
        }
        itemMapper.insertBatch(items);
        return new Persisted(order, items, lock.isDegraded());
    }

    // ==================== 支付（钱包在会员域先行，此处只做确认） ====================

    public PayableDTO payable(long orderId, long memberId) {
        BOrder order = mustGetOwned(orderId, memberId);
        BizAssert.isTrue(order.getOrderStatus() == StatusConsts.ORDER_WAIT_PAY,
                ErrorCode.ORDER_STATUS_ERROR, "订单不是待支付状态");
        PayableDTO dto = new PayableDTO();
        dto.setOrderId(order.getId());
        dto.setOrderNo(order.getOrderNo());
        dto.setAmount(order.getTotalAmount());
        return dto;
    }

    /** 幂等：已支付重复确认返回上次结果（balanceAfter 由 member 补齐） */
    public PayResultDTO payConfirm(long orderId, long memberId) {
        BOrder order = mustGetOwned(orderId, memberId);
        if (order.getOrderStatus() == StatusConsts.ORDER_PAID) {
            log.info("支付确认幂等命中（订单已支付，返回上次结果）：orderId={} orderNo={}", orderId, order.getOrderNo());
            return rebuildPayResult(order);
        }
        BizAssert.isTrue(order.getOrderStatus() == StatusConsts.ORDER_WAIT_PAY,
                ErrorCode.ORDER_STATUS_ERROR, "订单不是待支付状态");

        PayResultDTO[] resultHolder = new PayResultDTO[1];
        transactionTemplate.executeWithoutResult(status -> {
            stateMachine.transitionStrict(orderId, StatusConsts.ORDER_WAIT_PAY, StatusConsts.ORDER_PAID);
            stateMachine.fillTime(orderId, LocalDateTime.now(), true, false);
            itemMapper.update(null, Wrappers.<BOrderItem>lambdaUpdate()
                    .eq(BOrderItem::getOrderId, orderId)
                    .eq(BOrderItem::getItemStatus, StatusConsts.ITEM_WAIT_PAY)
                    .set(BOrderItem::getItemStatus, StatusConsts.ITEM_PAID));

            BPayment payment = new BPayment();
            payment.setPayNo(IdUtil.nextNo("P"));
            payment.setOrderId(orderId);
            payment.setMemberId(memberId);
            payment.setAmount(order.getTotalAmount());
            payment.setPayType(1);
            payment.setPayStatus(StatusConsts.PAY_SUCCESS);
            payment.setPayAt(LocalDateTime.now());
            paymentMapper.insert(payment);

            issueTickets(order);

            PayResultDTO result = new PayResultDTO();
            result.setOrderId(orderId);
            result.setOrderNo(order.getOrderNo());
            result.setPayNo(payment.getPayNo());
            result.setAmount(order.getTotalAmount());
            resultHolder[0] = result;
        });
        log.info("支付确认成功：orderId={} orderNo={} payNo={} 金额={}",
                orderId, order.getOrderNo(), resultHolder[0].getPayNo(), resultHolder[0].getAmount());
        return resultHolder[0];
    }

    /** 支付成功生成车票（明细逐张） */
    private void issueTickets(BOrder order) {
        List<BOrderItem> items = itemMapper.selectList(Wrappers.<BOrderItem>lambdaQuery()
                .eq(BOrderItem::getOrderId, order.getId()));
        if (items.isEmpty()) {
            log.warn("出票：订单无任何明细，未生成车票（明细数据缺失？）：orderNo={} orderId={}",
                    order.getOrderNo(), order.getId());
        }
        log.debug("出票：orderNo={} 明细 {} 条", order.getOrderNo(), items.size());
        for (BOrderItem item : items) {
            BTicket ticket = new BTicket();
            ticket.setMemberId(order.getMemberId());
            ticket.setOrderId(order.getId());
            ticket.setOrderItemId(item.getId());
            ticket.setPassengerId(item.getPassengerId());
            ticket.setPassengerName(item.getPassengerName());
            ticket.setIdCardNo(item.getIdCardNo());
            ticket.setRideDate(order.getRideDate());
            ticket.setTrainCode(order.getTrainCode());
            ticket.setFromStation(order.getFromStation());
            ticket.setToStation(order.getToStation());
            ticket.setDepartureTime(order.getDepartureTime());
            ticket.setArrivalTime(order.getArrivalTime());
            ticket.setSeatType(item.getSeatType());
            ticket.setCarriageNo(item.getCarriageNo());
            ticket.setSeatNo(item.getSeatNo());
            ticket.setTicketStatus(StatusConsts.TICKET_WAIT_RIDE);
            ticket.setPrice(item.getPrice());
            ticketMapper.insert(ticket);
        }
    }

    private PayResultDTO rebuildPayResult(BOrder order) {
        BPayment payment = paymentMapper.selectOne(Wrappers.<BPayment>lambdaQuery()
                .eq(BPayment::getOrderId, order.getId())
                .orderByDesc(BPayment::getId)
                .last("LIMIT 1"));
        PayResultDTO result = new PayResultDTO();
        result.setOrderId(order.getId());
        result.setOrderNo(order.getOrderNo());
        result.setAmount(order.getTotalAmount());
        if (payment != null) {
            result.setPayNo(payment.getPayNo());
        } else {
            log.warn("支付结果重建：订单已支付但查无支付流水：orderId={} orderNo={}", order.getId(), order.getOrderNo());
        }
        return result;
    }

    // ==================== 退款（申请 → 会员域入账 → 确认） ====================

    public RefundBatchDTO applyRefund(RefundApplyCmd cmd) {
        return transactionTemplate.execute(status -> {
            BOrder order = orderMapper.selectById(cmd.getOrderId());
            BizAssert.notNull(order, ErrorCode.ORDER_NOT_EXISTS);
            // 归属：购票人可退任意明细；乘车人本人可退"证件号与自己一致"的明细（他人代购票，凭操作者证件号比对）
            final String operatorIdCard;
            if (!Objects.equals(order.getMemberId(), cmd.getMemberId())) {
                BizAssert.isTrue(cmd.getOperatorIdCard() != null && !cmd.getOperatorIdCard().isBlank(),
                        ErrorCode.ITEM_NOT_REFUNDABLE, "非下单人退票需携带操作者证件号");
                operatorIdCard = cmd.getOperatorIdCard();
            } else {
                operatorIdCard = null;
            }
            // 车已到达则不可退（下车站到达时刻按乘车日跨天折算，与当前时间比较）
            Route route = routeService.resolve(order.getTrainCode());
            LocalDateTime arrival = route.arrivalAt(order.getRideDate(), order.getToStation());
            BizAssert.isTrue(LocalDateTime.now().isBefore(arrival),
                    ErrorCode.ORDER_STATUS_ERROR, "车次已到达，不可退票");
            BizAssert.isTrue(order.getOrderStatus() == StatusConsts.ORDER_PAID,
                    ErrorCode.ORDER_STATUS_ERROR, "仅已支付订单可退票");

            List<BOrderItem> allItems = itemMapper.selectList(Wrappers.<BOrderItem>lambdaQuery()
                    .eq(BOrderItem::getOrderId, cmd.getOrderId()));
            Map<Long, BOrderItem> itemMap = allItems.stream()
                    .collect(Collectors.toMap(BOrderItem::getId, Function.identity()));

            String batchNo = IdUtil.nextNo("RB");
            BigDecimal totalRefund = BigDecimal.ZERO;
            for (Long id : cmd.getItemIds()) {
                BOrderItem item = itemMap.get(id);
                BizAssert.notNull(item, ErrorCode.ITEM_NOT_REFUNDABLE);
                if (operatorIdCard != null) {
                    BizAssert.isTrue(Objects.equals(item.getIdCardNo(), operatorIdCard),
                            ErrorCode.ITEM_NOT_REFUNDABLE, "仅可退本人车票");
                }
                // 条件更新保证并发重复申请只有一次生效
                int rows = itemMapper.update(null, Wrappers.<BOrderItem>lambdaUpdate()
                        .eq(BOrderItem::getId, id)
                        .eq(BOrderItem::getItemStatus, StatusConsts.ITEM_PAID)
                        .set(BOrderItem::getItemStatus, StatusConsts.ITEM_REFUNDING)
                        .set(BOrderItem::getRefundBatchNo, batchNo));
                BizAssert.isTrue(rows == 1, ErrorCode.ITEM_NOT_REFUNDABLE, "明细 " + id + " 不可退");
                totalRefund = totalRefund.add(item.getPrice());
            }

            BRefundBatch batch = new BRefundBatch();
            batch.setBatchNo(batchNo);
            batch.setOrderId(order.getId());
            batch.setMemberId(cmd.getMemberId());
            batch.setRefundAmount(totalRefund);
            batch.setStatus(0);
            batch.setCreatedAt(LocalDateTime.now());
            refundBatchMapper.insert(batch);

            RefundBatchDTO dto = new RefundBatchDTO();
            dto.setBatchNo(batchNo);
            dto.setOrderId(order.getId());
            dto.setOrderNo(order.getOrderNo());
            dto.setItemIds(cmd.getItemIds());
            dto.setRefundAmount(totalRefund);
            log.info("退票申请成功：orderNo={} batchNo={} orderId={} 明细 {} 张 退款金额={}",
                    order.getOrderNo(), batchNo, order.getId(), cmd.getItemIds().size(), totalRefund);
            return dto;
        });
    }

    /** 确认退款：本地事务（明细已退 + 车票作废 + 批次完成 + 列车域释放座位）与 ORDER_REFUNDED 事件原子生效；幂等 */
    public RefundResultDTO confirmRefund(String batchNo) {
        BRefundBatch batch = mustGetBatch(batchNo);
        if (batch.getStatus() == 1) {
            log.info("退款确认幂等命中（批次已完成）：batchNo={}", batchNo);
            return rebuildRefundResult(batch);
        }
        RefundResultDTO[] holder = new RefundResultDTO[1];
        OrderRefundedEvent event = refundedEvent(batch);

        TransactionSendResult send = mqSender.sendInTransaction(
                MqConst.TOPIC_ORDER_EVENT, MqConst.TAG_ORDER_REFUNDED, batchNo, event,
                new TransactionalAction() {
                    @Override
                    public void executeLocal() {
                        holder[0] = doConfirmRefund(batch);
                    }

                    @Override
                    public RocketMQLocalTransactionState checkLocal(Message<?> message) {
                        // 回查：批次已确认 → 提交投递；批次还在 → 等下次回查
                        BRefundBatch fresh = refundBatchMapper.selectOne(Wrappers.<BRefundBatch>lambdaQuery()
                                .eq(BRefundBatch::getBatchNo, batchNo));
                        if (fresh != null && fresh.getStatus() == 1) {
                            return RocketMQLocalTransactionState.COMMIT;
                        }
                        return RocketMQLocalTransactionState.UNKNOWN;
                    }
                });
        if (send.getLocalTransactionState() != LocalTransactionState.COMMIT_MESSAGE) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR, "退款确认未生效，等待重试");
        }
        return holder[0];
    }

    /** 组装完整的退款确认事件（发送前快照；事务内以条件更新重新圈定明细，快照仅供事件携带） */
    private OrderRefundedEvent refundedEvent(BRefundBatch batch) {
        BOrder order = orderMapper.selectById(batch.getOrderId());
        BizAssert.notNull(order, ErrorCode.ORDER_NOT_EXISTS);
        OrderRefundedEvent event = new OrderRefundedEvent();
        event.setBatchNo(batch.getBatchNo());
        event.setOrderId(order.getId());
        event.setOrderNo(order.getOrderNo());
        event.setOrderItemIds(itemMapper.selectList(Wrappers.<BOrderItem>lambdaQuery()
                        .eq(BOrderItem::getOrderId, batch.getOrderId())
                        .eq(BOrderItem::getRefundBatchNo, batch.getBatchNo())
                        .eq(BOrderItem::getItemStatus, StatusConsts.ITEM_REFUNDING))
                .stream().map(BOrderItem::getId).toList());
        event.setRideDate(order.getRideDate());
        event.setFromStation(order.getFromStation());
        event.setToStation(order.getToStation());
        return event;
    }

    private RefundResultDTO doConfirmRefund(BRefundBatch batch) {
        BOrder order = orderMapper.selectById(batch.getOrderId());
        BizAssert.notNull(order, ErrorCode.ORDER_NOT_EXISTS);

        // Redis 锁包裹事务：座位位图读写的串行化覆盖到事务提交之后
        String lockKey = RedisKeys.orderLock(order.getRideDate().toString(), order.getTrainCode());
        if (!redisService.tryLock(lockKey, 3000, 15000)) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT, "座位释放竞争，等待重试");
        }
        try {
            return transactionTemplate.execute(txStatus -> {
                List<BOrderItem> items = itemMapper.selectList(Wrappers.<BOrderItem>lambdaQuery()
                        .eq(BOrderItem::getOrderId, batch.getOrderId())
                        .eq(BOrderItem::getRefundBatchNo, batch.getBatchNo())
                        .eq(BOrderItem::getItemStatus, StatusConsts.ITEM_REFUNDING));
                LocalDateTime now = LocalDateTime.now();
                List<Long> itemIds = new ArrayList<>(items.size());
                for (BOrderItem item : items) {
                    itemMapper.update(null, Wrappers.<BOrderItem>lambdaUpdate()
                            .eq(BOrderItem::getId, item.getId())
                            .eq(BOrderItem::getItemStatus, StatusConsts.ITEM_REFUNDING)
                            .set(BOrderItem::getItemStatus, StatusConsts.ITEM_REFUNDED)
                            .set(BOrderItem::getRefundAmount, item.getPrice())
                            .set(BOrderItem::getRefundAt, now));
                    ticketMapper.update(null, Wrappers.<BTicket>lambdaUpdate()
                            .eq(BTicket::getOrderItemId, item.getId())
                            .set(BTicket::getTicketStatus, StatusConsts.TICKET_REFUNDED));
                    itemIds.add(item.getId());
                }
                if (itemIds.isEmpty()) {
                    log.warn("退款确认：批次未命中任何退款中明细（明细缺失或状态已推进）：batchNo={} orderId={}",
                            batch.getBatchNo(), batch.getOrderId());
                }

                // 退款明细对应的座位在同一事务释放（同库本地事务，无需跨服务补偿）
                seatLockService.releaseItemsInTx(order.getOrderNo(), itemIds);

                // 全部明细退完 → 已支付→退款中→已关闭；部分退保持已支付
                long paidLeft = itemMapper.selectCount(Wrappers.<BOrderItem>lambdaQuery()
                        .eq(BOrderItem::getOrderId, batch.getOrderId())
                        .eq(BOrderItem::getItemStatus, StatusConsts.ITEM_PAID));
                boolean closed = false;
                if (paidLeft == 0) {
                    stateMachine.transitionStrict(batch.getOrderId(), StatusConsts.ORDER_PAID, StatusConsts.ORDER_REFUNDING);
                    stateMachine.transitionStrict(batch.getOrderId(), StatusConsts.ORDER_REFUNDING, StatusConsts.ORDER_CLOSED);
                    stateMachine.fillTime(batch.getOrderId(), now, false, true);
                    closed = true;
                }

                int rows = refundBatchMapper.update(null, Wrappers.<BRefundBatch>lambdaUpdate()
                        .eq(BRefundBatch::getId, batch.getId())
                        .eq(BRefundBatch::getStatus, 0)
                        .set(BRefundBatch::getStatus, 1)
                        .set(BRefundBatch::getConfirmedAt, now));
                BizAssert.isTrue(rows == 1, ErrorCode.ORDER_STATUS_ERROR, "退款批次已被确认");

                RefundResultDTO result = new RefundResultDTO();
                result.setOrderId(order.getId());
                result.setRefundedAmount(batch.getRefundAmount());
                result.setRefundedItemIds(itemIds);
                result.setOrderClosed(closed);
                log.info("退款确认完成：batchNo={} orderNo={} 退款明细 {} 张 金额={} 订单已关闭={}",
                        batch.getBatchNo(), order.getOrderNo(), itemIds.size(), batch.getRefundAmount(), closed);
                return result;
            });
        } finally {
            redisService.unlock(lockKey);
        }
    }

    public boolean refundConfirmed(String batchNo) {
        BRefundBatch batch = refundBatchMapper.selectOne(Wrappers.<BRefundBatch>lambdaQuery()
                .eq(BRefundBatch::getBatchNo, batchNo));
        return batch != null && batch.getStatus() == 1;
    }

    private BRefundBatch mustGetBatch(String batchNo) {
        BRefundBatch batch = refundBatchMapper.selectOne(Wrappers.<BRefundBatch>lambdaQuery()
                .eq(BRefundBatch::getBatchNo, batchNo));
        BizAssert.notNull(batch, ErrorCode.PARAM_ERROR, "退款批次不存在");
        return batch;
    }

    private RefundResultDTO rebuildRefundResult(BRefundBatch batch) {
        List<BOrderItem> items = itemMapper.selectList(Wrappers.<BOrderItem>lambdaQuery()
                .eq(BOrderItem::getRefundBatchNo, batch.getBatchNo()));
        long paidLeft = itemMapper.selectCount(Wrappers.<BOrderItem>lambdaQuery()
                .eq(BOrderItem::getOrderId, batch.getOrderId())
                .eq(BOrderItem::getItemStatus, StatusConsts.ITEM_PAID));
        RefundResultDTO result = new RefundResultDTO();
        result.setOrderId(batch.getOrderId());
        result.setRefundedAmount(batch.getRefundAmount());
        result.setRefundedItemIds(items.stream().map(BOrderItem::getId).toList());
        result.setOrderClosed(paidLeft == 0);
        return result;
    }

    // ==================== 关单（超时延迟消息 + 手动取消） ====================

    /**
     * 距支付截止（createdAt + pay-timeout-minutes）的剩余毫秒数。
     * 订单不存在或已非待支付返回 -1（消费端静默跳过）；已到期返回 <= 0。
     * 供超时关单延迟消息消费端复判：档位只是近似触发点，以业务截止时间为准。
     */
    public long remainingPayMillis(long orderId) {
        BOrder order = orderMapper.selectById(orderId);
        if (order == null || order.getOrderStatus() != StatusConsts.ORDER_WAIT_PAY) {
            return -1;
        }
        LocalDateTime deadline = order.getCreatedAt().plus(properties.payTimeout());
        return Duration.between(LocalDateTime.now(), deadline).toMillis();
    }

    /** 仅待支付可取消；本地取消 + 释放座位同一事务，与 ORDER_CLOSED 事务消息原子生效。返回 false = 非待支付/不存在 */
    public boolean cancelIfUnpaid(long orderId, int cancelType) {
        BOrder order = orderMapper.selectById(orderId);
        if (order == null || order.getOrderStatus() != StatusConsts.ORDER_WAIT_PAY) {
            return false;
        }
        TransactionSendResult send = mqSender.sendInTransaction(
                MqConst.TOPIC_ORDER_EVENT, MqConst.TAG_ORDER_CLOSED, order.getOrderNo(),
                closedEvent(order), new TransactionalAction() {
                    @Override
                    public void executeLocal() {
                        // Redis 锁包裹事务：座位位图读写的串行化覆盖到事务提交之后
                        String lockKey = RedisKeys.orderLock(order.getRideDate().toString(),
                                order.getTrainCode());
                        if (!redisService.tryLock(lockKey, 3000, 15000)) {
                            // 锁竞争：抛异常回滚半消息，由消费重试或 task 兜底扫描再触发
                            throw new BusinessException(ErrorCode.DATA_CONFLICT, "座位释放竞争，等待重试");
                        }
                        try {
                            transactionTemplate.executeWithoutResult(txStatus -> {
                                boolean ok = stateMachine.transition(orderId,
                                        StatusConsts.ORDER_WAIT_PAY, StatusConsts.ORDER_CANCELED);
                                if (!ok) {
                                    // 与支付确认并发竞争：抛异常触发半消息回滚，事件不投递
                                    throw new BusinessException(ErrorCode.ORDER_STATUS_ERROR, "订单状态已变化");
                                }
                                stateMachine.fillCancel(orderId, LocalDateTime.now(), cancelType);
                                seatLockService.releaseOrderInTx(order.getOrderNo());
                            });
                        } finally {
                            redisService.unlock(lockKey);
                        }
                    }

                    @Override
                    public RocketMQLocalTransactionState checkLocal(Message<?> message) {
                        BOrder fresh = orderMapper.selectById(orderId);
                        if (fresh == null) {
                            return RocketMQLocalTransactionState.ROLLBACK;
                        }
                        if (fresh.getOrderStatus() == StatusConsts.ORDER_CANCELED) {
                            return RocketMQLocalTransactionState.COMMIT;
                        }
                        return RocketMQLocalTransactionState.UNKNOWN;
                    }
                });
        boolean committed = send.getLocalTransactionState() == LocalTransactionState.COMMIT_MESSAGE;
        if (committed) {
            log.info("订单 {} 已关单（cancelType={}），座位已在本地事务释放", order.getOrderNo(), cancelType);
        }
        return committed;
    }

    private OrderClosedEvent closedEvent(BOrder order) {
        OrderClosedEvent event = new OrderClosedEvent();
        event.setOrderId(order.getId());
        event.setOrderNo(order.getOrderNo());
        event.setRideDate(order.getRideDate());
        event.setFromStation(order.getFromStation());
        event.setToStation(order.getToStation());
        return event;
    }

    // ==================== 出行/完成（管理端触发） ====================

    public void markTraveled(long orderId) {
        log.debug("订单推进已出行：orderId={}", orderId);
        transactionTemplate.executeWithoutResult(status -> {
            stateMachine.transitionStrict(orderId, StatusConsts.ORDER_PAID, StatusConsts.ORDER_TRAVELED);
            itemMapper.update(null, Wrappers.<BOrderItem>lambdaUpdate()
                    .eq(BOrderItem::getOrderId, orderId)
                    .eq(BOrderItem::getItemStatus, StatusConsts.ITEM_PAID)
                    .set(BOrderItem::getItemStatus, StatusConsts.ITEM_TRAVELED));
            ticketMapper.update(null, Wrappers.<BTicket>lambdaUpdate()
                    .eq(BTicket::getOrderId, orderId)
                    .eq(BTicket::getTicketStatus, StatusConsts.TICKET_WAIT_RIDE)
                    .set(BTicket::getTicketStatus, StatusConsts.TICKET_TRAVELED));
        });
    }

    public void complete(long orderId) {
        log.info("订单完成：orderId={}", orderId);
        stateMachine.transitionStrict(orderId, StatusConsts.ORDER_TRAVELED, StatusConsts.ORDER_COMPLETED);
    }

    // ==================== 查询 ====================

    public OrderDTO detail(long orderId, Long memberId) {
        BOrder order = orderMapper.selectById(orderId);
        BizAssert.notNull(order, ErrorCode.ORDER_NOT_EXISTS);
        if (memberId != null) {
            BizAssert.equal(order.getMemberId(), memberId, ErrorCode.ORDER_NOT_EXISTS);
        }
        List<BOrderItem> items = itemMapper.selectList(Wrappers.<BOrderItem>lambdaQuery()
                .eq(BOrderItem::getOrderId, orderId));
        log.debug("订单详情查询：orderId={} orderNo={} 状态={} 明细 {} 条", orderId, order.getOrderNo(),
                order.getOrderStatus(), items.size());
        return toDTO(order, items);
    }

    public PageResult<OrderDTO> page(OrderQuery query) {
        Page<BOrder> page = orderMapper.selectPage(new Page<>(query.getPageNum(), query.getPageSize()),
                Wrappers.<BOrder>lambdaQuery()
                        .eq(query.getMemberId() != null, BOrder::getMemberId, query.getMemberId())
                        .eq(query.getOrderStatus() != null, BOrder::getOrderStatus, query.getOrderStatus())
                        .in(query.getOrderStatuses() != null && !query.getOrderStatuses().isEmpty(),
                                BOrder::getOrderStatus, query.getOrderStatuses())
                        .eq(query.getRideDate() != null, BOrder::getRideDate, query.getRideDate())
                        .eq(query.getTrainCode() != null && !query.getTrainCode().isBlank(),
                                BOrder::getTrainCode, query.getTrainCode())
                        .le(query.getCreatedBefore() != null, BOrder::getCreatedAt, query.getCreatedBefore())
                        .orderByDesc(BOrder::getCreatedAt));
        log.info("订单分页查询：memberId={} status={} statuses={} rideDate={} trainCode={} 第 {} 页 命中 {} 条/共 {} 条",
                query.getMemberId(), query.getOrderStatus(), query.getOrderStatuses(), query.getRideDate(),
                query.getTrainCode(), query.getPageNum(), page.getRecords().size(), page.getTotal());
        return PageUtils.convert(page, o -> toDTO(o, null));
    }

    // ==================== 巡检（train-task 定时触发） ====================

    /** 关单兜底扫描：超时未支付订单批量关单（延迟消息漏网兜底），返回关单数 */
    public int sweepTimeout() {
        LocalDateTime deadline = LocalDateTime.now().minus(properties.payTimeout());
        List<BOrder> due = orderMapper.selectList(Wrappers.<BOrder>lambdaQuery()
                .eq(BOrder::getOrderStatus, StatusConsts.ORDER_WAIT_PAY)
                .lt(BOrder::getCreatedAt, deadline)
                .orderByAsc(BOrder::getId)
                .last("LIMIT 200"));
        int closed = 0;
        for (BOrder order : due) {
            try {
                if (cancelIfUnpaid(order.getId(), StatusConsts.CANCEL_TIMEOUT)) {
                    closed++;
                }
            } catch (Exception e) {
                log.warn("兜底关单失败 orderId={}: {}", order.getId(), e.getMessage());
            }
        }
        if (closed > 0) {
            log.info("兜底扫描关单 {} 笔", closed);
        }
        return closed;
    }

    // ==================== 组装 ====================

    private BOrder mustGetOwned(long orderId, long memberId) {
        BOrder order = orderMapper.selectById(orderId);
        BizAssert.notNull(order, ErrorCode.ORDER_NOT_EXISTS);
        BizAssert.equal(order.getMemberId(), memberId, ErrorCode.ORDER_NOT_EXISTS);
        return order;
    }

    private OrderDTO toDTO(BOrder order, List<BOrderItem> items) {
        return toDTO(order, items, null);
    }

    /** seatDegraded 仅下单响应携带（自选座降级提示）；详情/列表无此历史，恒为 null */
    private OrderDTO toDTO(BOrder order, List<BOrderItem> items, Boolean seatDegraded) {
        OrderDTO dto = new OrderDTO();
        dto.setId(order.getId());
        dto.setOrderNo(order.getOrderNo());
        dto.setMemberId(order.getMemberId());
        dto.setSeatDegraded(seatDegraded);
        dto.setRideDate(order.getRideDate());
        dto.setTrainCode(order.getTrainCode());
        dto.setFromStation(order.getFromStation());
        dto.setToStation(order.getToStation());
        Map<String, String> nameByCode = stationNameResolver.nameByCode();
        dto.setFromName(nameByCode.get(order.getFromStation()));
        dto.setToName(nameByCode.get(order.getToStation()));
        if (dto.getFromName() == null) {
            log.warn("订单站名解析失败（车站表缺该编码）：orderNo={} fromStation={}", order.getOrderNo(), order.getFromStation());
        }
        if (dto.getToName() == null) {
            log.warn("订单站名解析失败（车站表缺该编码）：orderNo={} toStation={}", order.getOrderNo(), order.getToStation());
        }
        dto.setDepartureTime(order.getDepartureTime());
        dto.setArrivalTime(order.getArrivalTime());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setOrderStatus(order.getOrderStatus());
        dto.setPayAt(order.getPayAt());
        dto.setCancelAt(order.getCancelAt());
        dto.setCancelType(order.getCancelType());
        dto.setClosedAt(order.getClosedAt());
        dto.setCreatedAt(order.getCreatedAt());
        if (items != null) {
            dto.setItems(items.stream().map(this::toItemDTO).collect(Collectors.toList()));
        }
        return dto;
    }

    private OrderItemDTO toItemDTO(BOrderItem item) {
        OrderItemDTO dto = new OrderItemDTO();
        dto.setId(item.getId());
        dto.setPassengerId(item.getPassengerId());
        dto.setPassengerName(item.getPassengerName());
        dto.setIdCardNo(item.getIdCardNo());
        dto.setTicketType(item.getTicketType());
        dto.setSeatType(item.getSeatType());
        dto.setCarriageNo(item.getCarriageNo());
        dto.setSeatNo(item.getSeatNo());
        dto.setFromStation(item.getFromStation());
        dto.setToStation(item.getToStation());
        dto.setPrice(item.getPrice());
        dto.setItemStatus(item.getItemStatus());
        dto.setRefundAmount(item.getRefundAmount());
        dto.setRefundAt(item.getRefundAt());
        return dto;
    }

    /** 下单事务内锁座 + 落库的产物 */
    private record Persisted(BOrder order, List<BOrderItem> items, boolean seatDegraded) {
    }
}
