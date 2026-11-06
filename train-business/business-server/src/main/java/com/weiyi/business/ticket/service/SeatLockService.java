package com.weiyi.business.ticket.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.common.core.util.BizAssert;
import com.weiyi.common.core.util.IdUtil;
import com.weiyi.business.api.dto.SeatDTO;
import com.weiyi.business.ticket.dto.Route;
import com.weiyi.business.ticket.dto.SeatLockCmd;
import com.weiyi.business.ticket.dto.SeatLockResult;
import com.weiyi.business.ticket.infra.entity.BDailyTrainSeat;
import com.weiyi.business.ticket.infra.entity.BDailyTrainTicket;
import com.weiyi.business.ticket.infra.entity.BSeatOccupy;
import com.weiyi.business.ticket.infra.mapper.BDailyTrainSeatMapper;
import com.weiyi.business.ticket.infra.mapper.BDailyTrainTicketMapper;
import com.weiyi.business.ticket.infra.mapper.BSeatOccupyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 锁座/释放（列车域聚合写，订单域同库直调）：
 * 锁 = 扣余票（原子 UPDATE 防超卖）+ 位图占座 + 写占用记录；
 * 释放 = 关单/退款同一事务调用，按占用记录 status 条件更新（1→2）保证幂等。
 *
 * <p>事务与锁边界由调用方（订单域 OrderService）掌控：
 * Redis 锁（"日期+车次"）包裹整个本地事务并覆盖到提交之后，串行化位图读写；
 * 本类方法均不加锁、不开事务，直接运行在调用方当前事务内。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatLockService {

    private final BDailyTrainTicketMapper ticketMapper;
    private final BDailyTrainSeatMapper seatMapper;
    private final BSeatOccupyMapper occupyMapper;
    private final RouteService routeService;

    // ==================== 锁座（订单域下单事务内调用） ====================

    /** 在调用方事务内锁座（调用方须已持有 RedisKeys.orderLock 分布式锁） */
    public SeatLockResult lockInTx(SeatLockCmd cmd) {
        int count = cmd.getOrderItemIds().size();
        BizAssert.isTrue(count == cmd.getCount(), ErrorCode.PARAM_ERROR, "明细数与购票数不一致");
        log.info("锁座开始：orderNo={} {} {} {} → {} 席别={} 张数={} 自选座={}",
                cmd.getOrderNo(), cmd.getRideDate(), cmd.getTrainCode(),
                cmd.getFromStation(), cmd.getToStation(), cmd.getSeatType(), count, cmd.getChosenSeats());

        // 幂等：同一 orderNo 重放（消息重试/并发重入）直接返回已有座位，不重复扣减
        List<BSeatOccupy> existing = occupyMapper.selectList(Wrappers.<BSeatOccupy>lambdaQuery()
                .eq(BSeatOccupy::getOrderNo, cmd.getOrderNo())
                .orderByAsc(BSeatOccupy::getId));
        if (!existing.isEmpty()) {
            log.info("锁座幂等命中：orderNo={} 已有 {} 条占用记录，复用原座位", cmd.getOrderNo(), existing.size());
            return rebuildResult(cmd, existing);
        }

        Route route = routeService.resolve(cmd.getTrainCode());
        int fromIdx = route.indexOf(cmd.getFromStation());
        int toIdx = route.indexOf(cmd.getToStation());
        BizAssert.isTrue(toIdx > fromIdx, ErrorCode.PARAM_ERROR, "到达站必须晚于出发站");

        BDailyTrainTicket ticketRow = ticketMapper.selectOne(Wrappers.<BDailyTrainTicket>lambdaQuery()
                .eq(BDailyTrainTicket::getRideDate, cmd.getRideDate())
                .eq(BDailyTrainTicket::getTrainCode, cmd.getTrainCode())
                .eq(BDailyTrainTicket::getFromStation, cmd.getFromStation())
                .eq(BDailyTrainTicket::getToStation, cmd.getToStation())
                .eq(BDailyTrainTicket::getSeatType, cmd.getSeatType()));
        BizAssert.notNull(ticketRow, ErrorCode.TRAIN_NOT_EXISTS, "该席别未开售");
        log.debug("锁座余票行：orderNo={} 余票={} 票价={}", cmd.getOrderNo(), ticketRow.getRemaining(), ticketRow.getPrice());

        // 原子扣减余票（防超卖）
        BizAssert.isTrue(ticketMapper.deductRemaining(cmd.getRideDate(), cmd.getTrainCode(),
                cmd.getFromStation(), cmd.getToStation(), cmd.getSeatType(), count) == 1,
                ErrorCode.TICKET_NOT_ENOUGH);
        log.debug("余票已扣减：orderNo={} 扣减 {} 张", cmd.getOrderNo(), count);

        List<BDailyTrainSeat> seats;
        if (cmd.getChosenSeats() == null || cmd.getChosenSeats().isEmpty()) {
            seats = seatMapper.selectIdleInRange(cmd.getRideDate(), cmd.getTrainCode(),
                    cmd.getSeatType(), fromIdx, toIdx);
        } else {
            seats = seatMapper.selectList(Wrappers.<BDailyTrainSeat>lambdaQuery()
                    .eq(BDailyTrainSeat::getRideDate, cmd.getRideDate())
                    .eq(BDailyTrainSeat::getTrainCode, cmd.getTrainCode())
                    .eq(BDailyTrainSeat::getSeatType, cmd.getSeatType())
                    .orderByAsc(BDailyTrainSeat::getCarriageNo)
                    .orderByAsc(BDailyTrainSeat::getRowNo)
                    .orderByAsc(BDailyTrainSeat::getColNo));
        }
        List<BDailyTrainSeat> chosen;
        boolean degraded = false;
        if (cmd.getChosenSeats() != null && !cmd.getChosenSeats().isEmpty()) {
            // 用户自选座（抽象列位偏移算法）：选不到（如被抢先支付占座）降级系统自动分配，不出无座票；
            // 非法入参（座位号不存在/席别不匹配/重复）仍拒绝，避免掩盖前端缺陷
            try {
                chosen = SeatChooser.choose(seats, cmd.getSeatType(), fromIdx, toIdx, cmd.getChosenSeats());
            } catch (BusinessException e) {
                if (e.getCode() != ErrorCode.SEAT_ALLOC_FAIL.getCode()) {
                    throw e;
                }
                log.info("自选座不可用，已降级自动分配：{} {} {}", cmd.getRideDate(), cmd.getTrainCode(), cmd.getChosenSeats());
                degraded = true;
                chosen = SeatAllocator.DEFAULT.allocate(seats, cmd.getSeatType(), fromIdx, toIdx, count);
            }
        } else {
            chosen = SeatAllocator.DEFAULT.allocate(seats, cmd.getSeatType(), fromIdx, toIdx, count);
        }
        LocalDateTime now = LocalDateTime.now();
        List<BSeatOccupy> occupies = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BDailyTrainSeat seat = chosen.get(i);
            seat.setSell(SeatBitmap.mark(seat.getSell(), fromIdx, toIdx));
            log.debug("占座：orderNo={} 车厢{} 排{} 列{}", cmd.getOrderNo(),
                    seat.getCarriageNo(), seat.getRowNo(), seat.getColNo());

            BSeatOccupy occupy = new BSeatOccupy();
            occupy.setId(IdUtil.nextId());
            occupy.setOrderNo(cmd.getOrderNo());
            occupy.setOrderItemId(cmd.getOrderItemIds().get(i));
            occupy.setRideDate(cmd.getRideDate());
            occupy.setTrainCode(cmd.getTrainCode());
            occupy.setFromStation(cmd.getFromStation());
            occupy.setToStation(cmd.getToStation());
            occupy.setSeatType(cmd.getSeatType());
            occupy.setCarriageNo(seat.getCarriageNo());
            occupy.setRowNo(seat.getRowNo());
            occupy.setColNo(seat.getColNo());
            occupy.setStatus(1);
            occupy.setCreatedAt(now);
            occupy.setUpdatedAt(now);
            occupies.add(occupy);
        }
        seatMapper.batchUpdateSell(chosen);
        occupyMapper.insertBatch(occupies);
        log.debug("批量占座落库：orderNo={} 座位 {} 行、占用记录 {} 行（各一次往返）", cmd.getOrderNo(), chosen.size(), occupies.size());
        SeatLockResult result = buildResult(cmd, ticketRow, route, chosen, degraded);
        log.info("锁座完成：orderNo={} 分配 {} 个座位{} 单价={}", cmd.getOrderNo(), chosen.size(),
                degraded ? "（自选座降级）" : "", result.getUnitPrice());
        return result;
    }

    // ==================== 释放（订单域关单/退款事务内调用） ====================

    /** 关单：释放该订单全部仍占用的座位（幂等，重复调用无副作用）；返回释放条数 */
    public int releaseOrderInTx(String orderNo) {
        List<BSeatOccupy> locked = occupyMapper.selectList(Wrappers.<BSeatOccupy>lambdaQuery()
                .eq(BSeatOccupy::getOrderNo, orderNo)
                .eq(BSeatOccupy::getStatus, 1));
        return doRelease(locked);
    }

    /** 退款：只释放本批次明细对应的座位（幂等）；返回释放条数 */
    public int releaseItemsInTx(String orderNo, List<Long> orderItemIds) {
        List<BSeatOccupy> locked = occupyMapper.selectList(Wrappers.<BSeatOccupy>lambdaQuery()
                .eq(BSeatOccupy::getOrderNo, orderNo)
                .eq(BSeatOccupy::getStatus, 1)
                .in(BSeatOccupy::getOrderItemId, orderItemIds));
        return doRelease(locked);
    }

    private int doRelease(List<BSeatOccupy> locked) {
        if (locked.isEmpty()) {
            log.debug("座位释放：无仍占用的占用记录，跳过");
            return 0;
        }
        BSeatOccupy first = locked.get(0);
        Route route = routeService.resolve(first.getTrainCode());
        int fromIdx = route.indexOf(first.getFromStation());
        int toIdx = route.indexOf(first.getToStation());
        int count = 0;
        for (BSeatOccupy occupy : locked) {
            BDailyTrainSeat seat = seatMapper.selectOne(Wrappers.<BDailyTrainSeat>lambdaQuery()
                    .eq(BDailyTrainSeat::getRideDate, occupy.getRideDate())
                    .eq(BDailyTrainSeat::getTrainCode, occupy.getTrainCode())
                    .eq(BDailyTrainSeat::getCarriageNo, occupy.getCarriageNo())
                    .eq(BDailyTrainSeat::getRowNo, occupy.getRowNo())
                    .eq(BDailyTrainSeat::getColNo, occupy.getColNo()));
            if (seat != null) {
                seat.setSell(SeatBitmap.clear(seat.getSell(), fromIdx, toIdx));
                seatMapper.updateById(seat);
            } else {
                log.warn("座位释放：占用记录对应的座位行缺失，仅释放占用 status：occupyId={} {} {} 车厢{} 排{} 列{}",
                        occupy.getId(), occupy.getRideDate(), occupy.getTrainCode(),
                        occupy.getCarriageNo(), occupy.getRowNo(), occupy.getColNo());
            }
            // 条件更新占用状态：并发/重入时仅一方成功，余票回补不重复
            int rows = occupyMapper.update(null, Wrappers.<BSeatOccupy>lambdaUpdate()
                    .eq(BSeatOccupy::getId, occupy.getId())
                    .eq(BSeatOccupy::getStatus, 1)
                    .set(BSeatOccupy::getStatus, 2)
                    .set(BSeatOccupy::getUpdatedAt, LocalDateTime.now()));
            count += rows;
        }
        if (count > 0) {
            ticketMapper.increaseRemaining(first.getRideDate(), first.getTrainCode(),
                    first.getFromStation(), first.getToStation(), first.getSeatType(), count);
        }
        log.info("座位释放完成：orderNo={} 待释放 {} 条，实际释放 {} 条，余票回补 {}",
                first.getOrderNo(), locked.size(), count, count > 0 ? count : 0);
        return count;
    }

    // ==================== 结果组装 ====================

    private SeatLockResult buildResult(SeatLockCmd cmd, BDailyTrainTicket ticketRow,
                                       Route route, List<BDailyTrainSeat> chosen, boolean degraded) {
        SeatLockResult result = new SeatLockResult();
        result.setOrderNo(cmd.getOrderNo());
        result.setSeats(chosen.stream().map(this::toSeatDTO).toList());
        result.setUnitPrice(ticketRow.getPrice());
        result.setDepartureTime(route.departureAt(cmd.getRideDate(), cmd.getFromStation()));
        result.setArrivalTime(route.arrivalAt(cmd.getRideDate(), cmd.getToStation()));
        result.setDegraded(degraded);
        return result;
    }

    private SeatLockResult rebuildResult(SeatLockCmd cmd, List<BSeatOccupy> existing) {
        BDailyTrainTicket ticketRow = ticketMapper.selectOne(Wrappers.<BDailyTrainTicket>lambdaQuery()
                .eq(BDailyTrainTicket::getRideDate, cmd.getRideDate())
                .eq(BDailyTrainTicket::getTrainCode, cmd.getTrainCode())
                .eq(BDailyTrainTicket::getFromStation, cmd.getFromStation())
                .eq(BDailyTrainTicket::getToStation, cmd.getToStation())
                .eq(BDailyTrainTicket::getSeatType, cmd.getSeatType()));
        SeatLockResult result = new SeatLockResult();
        result.setOrderNo(cmd.getOrderNo());
        result.setSeats(existing.stream().map(o -> {
            SeatDTO dto = new SeatDTO();
            dto.setCarriageNo(o.getCarriageNo());
            dto.setRowNo(o.getRowNo());
            dto.setColNo(o.getColNo());
            dto.setSeatNo(o.getRowNo() + o.getColNo());
            return dto;
        }).toList());
        if (ticketRow != null) {
            result.setUnitPrice(ticketRow.getPrice());
        } else {
            log.warn("锁座幂等重建：余票票价行缺失，单价为 null：orderNo={} {} {} {} → {} 席别={}",
                    cmd.getOrderNo(), cmd.getRideDate(), cmd.getTrainCode(),
                    cmd.getFromStation(), cmd.getToStation(), cmd.getSeatType());
        }
        return result;
    }

    private SeatDTO toSeatDTO(BDailyTrainSeat seat) {
        SeatDTO dto = new SeatDTO();
        dto.setCarriageNo(seat.getCarriageNo());
        dto.setRowNo(seat.getRowNo());
        dto.setColNo(seat.getColNo());
        dto.setSeatNo(seat.getRowNo() + seat.getColNo());
        return dto;
    }
}
