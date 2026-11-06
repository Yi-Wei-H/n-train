package com.weiyi.member.service;

import com.weiyi.common.core.constant.StatusConsts;
import com.weiyi.common.core.util.AesUtil;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.result.PageResult;
import com.weiyi.common.redis.constant.RedisKeys;
import com.weiyi.common.redis.ratelimit.SlidingWindowRateLimiter;
import com.weiyi.common.redis.service.RedisService;
import com.weiyi.common.security.context.LoginContext;
import com.weiyi.member.config.MemberProperties;
import com.weiyi.member.infra.entity.MPassenger;
import com.weiyi.member.infra.entity.MWalletFlow;
import com.weiyi.member.infra.mapper.MPassengerMapper;
import com.weiyi.business.api.api.MyTicketApi;
import com.weiyi.business.api.api.OrderApi;
import com.weiyi.business.api.dto.MyTicketDTO;
import com.weiyi.business.api.dto.OrderCreateCmd;
import com.weiyi.business.api.dto.OrderDTO;
import com.weiyi.business.api.dto.OrderPassengerCmd;
import com.weiyi.business.api.dto.OrderQuery;
import com.weiyi.business.api.dto.PayableDTO;
import com.weiyi.business.api.dto.PayResultDTO;
import com.weiyi.business.api.dto.RefundApplyCmd;
import com.weiyi.business.api.dto.RefundBatchDTO;
import com.weiyi.business.api.dto.RefundResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 订单编排（会员域视角，钱包在本域本地事务；原 Redisson 延时队列已由订单域
 * RocketMQ 延迟消息取代，Quartz 扫描保留为兜底）：
 * <ul>
 *   <li>下单：频次/验证码 → 校验乘车人归属并快照 → 订单域（内部同步锁座）→ 失效余票缓存</li>
 *   <li>支付：payable 校验 → 钱包幂等扣款 → 订单域确认；确认失败且订单未支付 → 钱包冲正（补偿）</li>
 *   <li>退款：订单域申请 → 钱包幂等入账 → 订单域确认（事务消息释放座位）；
 *       确认中断由对账扫描重试（Quartz 每分钟）</li>
 *   <li>关单：订单域延迟消息主路径 + 本服务兜底扫描</li>
 * </ul>
 * 支付时限对齐 RocketMQ 固定延迟档位：默认 10 分钟（订单域 14 档）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderBizService {

    private final OrderApi orderApi;
    private final MyTicketApi myTicketApi;
    private final WalletTxService walletTxService;
    private final MPassengerMapper passengerMapper;
    private final RedisService redisService;
    private final CaptchaService captchaService;
    private final TicketBizService ticketBizService;
    private final MemberProperties properties;
    private final MemberUserService memberUserService;
    private final SlidingWindowRateLimiter slidingWindowRateLimiter;

    // ==================== 下单 ====================

    public OrderDTO create(OrderCreateCmd cmd, List<Long> passengerIds, String captchaKey, String captchaCode) {
        long memberId = LoginContext.requireMemberId();
        cmd.setMemberId(memberId);
        log.info("下单编排开始：member={} {} {} {} → {} 席别={} 乘车人 {} 人",
                memberId, cmd.getRideDate(), cmd.getTrainCode(), cmd.getFromStation(),
                cmd.getToStation(), cmd.getSeatType(), passengerIds.size());

        // ① 触发过验证码标记 → 必须先通过图形验证码
        if (redisService.hasKey(RedisKeys.orderCaptchaRequired(memberId))) {
            captchaService.verifyAndConsume(captchaKey, captchaCode);
            redisService.delete(RedisKeys.orderCaptchaRequired(memberId));
        }

        // ② per-user 滑动窗口只计数不拒绝：超阈值打验证码标记（下次下单生效）
        Boolean slidingWindow = redisService.slidingWindow(RedisKeys.orderFreq(memberId),
                properties.getCaptchaWindowSeconds() * 1000L, properties.getCaptchaThreshold());
        if (!slidingWindow) {
            redisService.set(RedisKeys.orderCaptchaRequired(memberId), "1", Duration.ofMinutes(10));
        }

        // ③ 乘车人归属校验 + 快照（订单域不再回查会员库）
        cmd.setPassengers(buildPassengerSnapshot(memberId, passengerIds));

        // ④ 订单域下单（内部：列车域同步锁座 → 订单落库 → 超时关单延迟消息投递）
        OrderDTO order = orderApi.createOrder(cmd);
        log.info("下单编排完成：orderNo={} orderId={} 金额={}", order.getOrderNo(), order.getId(), order.getTotalAmount());

        // ⑤ 精准失效余票缓存
        ticketBizService.evictTicketCache(cmd.getRideDate(), cmd.getFromStation(), cmd.getToStation());
        return order;
    }

    private List<OrderPassengerCmd> buildPassengerSnapshot(long memberId, List<Long> passengerIds) {
        List<MPassenger> passengers = passengerMapper.selectBatchIds(passengerIds);
        passengers.removeIf(p -> !p.getMemberId().equals(memberId));
        if (passengers.size() != passengerIds.size()) {
            log.warn("乘车人归属校验失败：member={} 传入 {} 个，归属匹配 {} 个：ids={}",
                    memberId, passengerIds.size(), passengers.size(), passengerIds);
            throw new BusinessException(ErrorCode.PASSENGER_NOT_EXISTS);
        }
        Map<Long, MPassenger> byId = passengers.stream()
                .collect(Collectors.toMap(MPassenger::getId, Function.identity()));
        return passengerIds.stream().map(id -> {
            MPassenger p = byId.get(id);
            OrderPassengerCmd snapshot = new OrderPassengerCmd();
            snapshot.setPassengerId(p.getId());
            snapshot.setName(p.getName());
            snapshot.setIdCardNo(p.getIdCardNo());
            snapshot.setTicketType(p.getPassengerType());
            return snapshot;
        }).toList();
    }

    // ==================== 支付（钱包先行 + 确认 + 冲正补偿） ====================

    public PayResultDTO pay(long orderId) {
        long memberId = LoginContext.requireMemberId();
        PayableDTO payable = orderApi.payable(orderId, memberId);
        log.info("支付编排开始：member={} orderId={} orderNo={} 应付金额={}",
                memberId, orderId, payable.getOrderNo(), payable.getAmount());

        // ① 本域本地事务：钱包幂等扣款（bizNo=订单号，重试不重复扣）
        BigDecimal balanceAfter = walletTxService.deductIdempotent(memberId, payable.getAmount(),
                payable.getOrderNo(), "支付订单 " + payable.getOrderNo());
        log.info("钱包扣款完成：member={} orderNo={} 扣后余额={}", memberId, payable.getOrderNo(), balanceAfter);

        // ② 订单域确认（幂等：已支付原样返回）
        PayResultDTO result;
        try {
            result = orderApi.payConfirm(orderId, memberId);
        } catch (Exception e) {
            // ③ 确认失败：回查订单真实状态——仍未支付则冲正退款（与超时关单的竞争由订单域状态机裁决）
            OrderDTO order = orderApi.detail(orderId, memberId);
            if (order.getOrderStatus() != StatusConsts.ORDER_PAID) {
                BigDecimal compensated = walletTxService.compensate(
                        memberId, payable.getAmount(), payable.getOrderNo());
                log.warn("支付确认失败已冲正：orderId={} balance={} err={}", orderId, compensated, e.getMessage());
                throw e;
            }
            // 确认其实已生效（超时网络抖动），按成功补齐结果
            log.info("支付确认异常但订单已支付，按成功处理：orderId={}", orderId);
            result = new PayResultDTO();
            result.setOrderId(orderId);
            result.setOrderNo(order.getOrderNo());
            result.setAmount(payable.getAmount());
        }
        result.setBalanceAfter(balanceAfter);
        return result;
    }

    // ==================== 退票（申请 → 入账 → 确认；对账扫描兜底） ====================

    public RefundResultDTO refund(long orderId, List<Long> itemIds) {
        long memberId = LoginContext.requireMemberId();
        OrderDTO order = orderApi.detail(orderId, null);
        BizAssert.notNull(order, ErrorCode.ORDER_NOT_EXISTS);
        log.info("退票编排开始：member={} orderId={} orderNo={} 退 {} 张明细 {}",
                memberId, orderId, order.getOrderNo(), itemIds.size(), itemIds);
        RefundApplyCmd applyCmd = new RefundApplyCmd();
        applyCmd.setMemberId(memberId);
        applyCmd.setOrderId(orderId);
        applyCmd.setItemIds(itemIds);
        if (order.getMemberId() != memberId) {
            // 乘车人本人退他人代购票：携带操作者身份证号，订单域逐明细比对证件一致才放行
            applyCmd.setOperatorIdCard(memberUserService.getIdCardNo(memberId));
        }

        // ① 订单域申请（明细转退款中，生成退款批次；归属/到时校验在订单域）
        RefundBatchDTO batch = orderApi.applyRefund(applyCmd);

        // ② 本域本地事务：钱包幂等入账（batchNo 幂等键，重试不重复入账）
        //    入账对象 = 原购票人（下单人），不是当前操作者
        walletTxService.creditIdempotent(order.getMemberId(), batch.getRefundAmount(), batch.getBatchNo(),
                "退票 " + batch.getItemIds().size() + " 张 " + batch.getOrderNo());

        // ③ 订单域确认（本地事务 + 事务消息释放座位）；失败留给对账扫描重试，钱已到账不回退
        RefundResultDTO result;
        try {
            result = orderApi.confirmRefund(batch.getBatchNo());
        } catch (Exception e) {
            log.warn("退款确认暂未完成，等待对账扫描重试：batchNo={} err={}", batch.getBatchNo(), e.getMessage());
            result = new RefundResultDTO();
            result.setOrderId(orderId);
            result.setRefundedAmount(batch.getRefundAmount());
            result.setRefundedItemIds(batch.getItemIds());
        }
        // ④ 缓存失效（座位释放已在订单域确认事务内本地完成，这里提前失效余票缓存即可）
        ticketBizService.evictTicketCache(order.getRideDate(), order.getFromStation(), order.getToStation());
        result.setBalanceAfter(walletTxService.getWallet(order.getMemberId()).getBalance());
        return result;
    }

    /** 对账扫描：钱包已入账但订单域未确认的退款批次 → 重试确认（Quartz 每分钟兜底） */
    public int sweepRefundConfirms() {
        LocalDateTime since = LocalDateTime.now().minusHours(2);
        int confirmed = 0;
        for (MWalletFlow flow : walletTxService.refundFlowsSince(since)) {
            String batchNo = flow.getBizNo();
            if (batchNo == null || batchNo.contains("-")) {
                continue;  // 冲正流水（orderNo-RV）不是退款批次
            }
            try {
                if (!orderApi.refundConfirmed(batchNo)) {
                    RefundResultDTO result = orderApi.confirmRefund(batchNo);
                    log.info("对账扫描完成退款确认：batchNo={} items={}", batchNo,
                            result.getRefundedItemIds() == null ? 0 : result.getRefundedItemIds().size());
                    confirmed++;
                }
            } catch (Exception e) {
                log.warn("对账扫描退款确认失败 batchNo={}: {}", batchNo, e.getMessage());
            }
        }
        if (confirmed > 0) {
            log.info("对账扫描补确认退款批次 {} 个", confirmed);
        }
        return confirmed;
    }

    // ==================== 取消 / 查询 ====================

    public void cancel(long orderId) {
        long memberId = LoginContext.requireMemberId();
        log.info("会员取消订单（仅待支付可取消）：member={} orderId={}", memberId, orderId);
        orderApi.cancelIfUnpaid(orderId, StatusConsts.CANCEL_MANUAL);
    }

    public OrderDTO detail(long orderId) {
        return orderApi.detail(orderId, LoginContext.requireMemberId());
    }

    public PageResult<OrderDTO> myOrders(List<Integer> orderStatus, long pageNum, long pageSize) {
        OrderQuery query = new OrderQuery();
        query.setMemberId(LoginContext.requireMemberId());
        query.setOrderStatuses(orderStatus);
        query.setPageNum(pageNum);
        query.setPageSize(pageSize);
        return orderApi.page(query);
    }

    public List<MyTicketDTO> myTickets(Integer ticketStatus) {
        long memberId = LoginContext.requireMemberId();
        String idCard = memberUserService.getIdCardNo(memberId);
        String idCardCipher = (idCard == null || idCard.isBlank()) ? null : AesUtil.encrypt(idCard);
        List<MyTicketDTO> tickets = myTicketApi.listByMember(memberId, idCardCipher, ticketStatus);
        log.debug("我的车票查询完成：member={} status={} 命中 {} 张", memberId, ticketStatus, tickets.size());
        return tickets;
    }

    // ==================== 退款对账兜底（train-task 定时触发；主路径：支付/退款同步确认） ====================
}
