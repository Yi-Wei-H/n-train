package com.weiyi.business.ticket.service;

import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.business.ticket.infra.entity.BDailyTrainSeat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户自选座（旧版偏移算法）：前端传抽象列位（列字母 + 第 1/2 排相对位，如 "C1"/"D2"），
 * 后端按席别列布局构建两排参照表，把所选座位换算成相对首座位的物理偏移，
 * 再逐车厢按物理序（车厢→排→列）找首个"首列匹配 + 区间位图空闲 + 偏移座位同车厢且空闲"的组合。
 * 不跨车厢；选不到抛 SEAT_ALLOC_FAIL，由调用方决定降级自动分配或失败回滚（本类不含降级策略）。
 */
public final class SeatChooser {

    private SeatChooser() {
    }

    public static List<BDailyTrainSeat> choose(List<BDailyTrainSeat> seats, int seatType,
                                               int fromIdx, int toIdx, List<String> chosenSeats) {
        if (chosenSeats == null || chosenSeats.isEmpty() || chosenSeats.size() != new HashSet<>(chosenSeats).size()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "座位选择非法");
        }
        // 参照表：席别全部列 × 两排（如一等座 [A1,C1,D1,F1,A2,C2,D2,F2]），顺序与物理序一致
        List<String> cols = SeatLayouts.colsOf(seatType);
        List<String> refer = new ArrayList<>();
        for (int row = 1; row <= 2; row++) {
            for (String col : cols) {
                refer.add(col + row);
            }
        }
        List<Integer> offsets = new ArrayList<>();
        int base = -1;
        for (String seat : chosenSeats) {
            int index = refer.indexOf(seat);
            if (index < 0) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "座位选择非法：" + seat);
            }
            if (base < 0) {
                base = index;
            }
            offsets.add(index - base);
        }
        String firstCol = chosenSeats.get(0).substring(0, 1);

        // 逐车厢（seats 已按车厢分段）在物理序上找偏移组合
        int n = seats.size();
        int start = 0;
        while (start < n) {
            int carriage = seats.get(start).getCarriageNo();
            int end = start;
            while (end < n && seats.get(end).getCarriageNo() == carriage) {
                end++;
            }
            for (int i = start; i < end; i++) {
                BDailyTrainSeat first = seats.get(i);
                if (!firstCol.equals(first.getColNo())) {
                    continue;
                }
                if (!SeatBitmap.isFree(first.getSell(), fromIdx, toIdx)) {
                    continue;
                }
                List<BDailyTrainSeat> group = new ArrayList<>();
                group.add(first);
                boolean allFree = true;
                for (int j = 1; j < offsets.size(); j++) {
                    int next = i + offsets.get(j);
                    if (next >= end) {          // 偏移超出本车厢 → 不跨车厢，本组合失败
                        allFree = false;
                        break;
                    }
                    BDailyTrainSeat seat = seats.get(next);
                    if (!SeatBitmap.isFree(seat.getSell(), fromIdx, toIdx)) {
                        allFree = false;
                        break;
                    }
                    group.add(seat);
                }
                if (allFree) {
                    return group;
                }
            }
            start = end;
        }
        throw new BusinessException(ErrorCode.SEAT_ALLOC_FAIL, "所选座位已不可用，请重新选择");
    }

    /** 供前端判断是否开放选座：一等座(2)/二等座(3) 且余票充足（旧版阈值 20） */
    public static boolean supported(Integer seatType, int remaining) {
        Set<Integer> choosable = Set.of(2, 3);
        return seatType != null && choosable.contains(seatType) && remaining >= 20;
    }
}
