package com.weiyi.business.ticket.service;

import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;
import com.weiyi.business.ticket.infra.entity.BDailyTrainSeat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 连座优先选座器。
 * 约定：入参 seats 必须已按 (carriageNo, rowNo, colNo) 升序排列（由调用方 orderBy 保证）。
 * 只负责"选"，不改位图——选中后由调用方统一 mark + update，与现有事务流程一致。
 * 席别编号与 {@link SeatLayouts#COL_LAYOUT} 一致；未定义的席别（硬卧/硬座等）自动退化为散座分配。
 */
public class SeatAllocator {

    private final Map<Integer, List<List<String>>> layouts;

    public SeatAllocator(Map<Integer, List<List<String>>> layouts) {
        this.layouts = layouts;
    }

    public static final SeatAllocator DEFAULT = new SeatAllocator(Map.of(
            1, List.of(List.of("A"),         List.of("C", "F")),    // 商务座：A   | 过道 | CF
            2, List.of(List.of("A", "C"),    List.of("D", "F")),    // 一等座：AC  | 过道 | DF
            3, List.of(List.of("A", "B", "C"), List.of("D", "F"))   // 二等座：ABC | 过道 | DF
    ));

    public List<BDailyTrainSeat> allocate(List<BDailyTrainSeat> seats,
                                          int seatType, int fromIdx, int toIdx, int count) {
        // 过滤区间空闲，按 (车厢,排) 分组；LinkedHashMap 保持物理顺序
        Map<String, List<BDailyTrainSeat>> rows = new LinkedHashMap<>();
        for (BDailyTrainSeat seat : seats) {
            if (SeatBitmap.isFree(seat.getSell(), fromIdx, toIdx)) {
                rows.computeIfAbsent(seat.getCarriageNo() + ":" + seat.getRowNo(),
                        k -> new ArrayList<>()).add(seat);
            }
        }

        // ② 逐行找连座段，全局取"富余最少"（best-fit：3 人配 3 连座优于配 5 连座）
        List<BDailyTrainSeat> best = null;
        for (List<BDailyTrainSeat> row : rows.values()) {
            List<BDailyTrainSeat> run = bestRunInRow(row, seatType, count);
            if (run != null && (best == null || run.size() < best.size())) {
                best = run;
                if (best.size() == count) {
                    break;  // 无富余，物理顺序上不可能更优
                }
            }
        }
        if (best != null) {
            return best;
        }

        // ③ 全车无连座 → 降级散座（物理顺序先到先得，与现状行为一致）
        List<BDailyTrainSeat> scattered = new ArrayList<>(count);
        for (List<BDailyTrainSeat> row : rows.values()) {
            for (BDailyTrainSeat seat : row) {
                scattered.add(seat);
                if (scattered.size() == count) {
                    return scattered;
                }
            }
        }
        throw new BusinessException(ErrorCode.SEAT_ALLOC_FAIL);  // 余票校验已拦截，防御性兜底
    }

    /**
     * 行内找"走道同侧、长度 ≥ count"的连续空位段，返回富余最少的一段（截取尾部 count 个）；无则 null。
     * 该列不存在（本排布局缺列）或已占，段即断开。
     */
    private List<BDailyTrainSeat> bestRunInRow(List<BDailyTrainSeat> rowSeats, int seatType, int count) {
        Map<String, BDailyTrainSeat> byCol = new HashMap<>();
        rowSeats.forEach(s -> byCol.put(s.getColNo(), s));

        List<BDailyTrainSeat> best = null;
        for (List<String> side : layouts.getOrDefault(seatType, List.of())) {
            List<BDailyTrainSeat> run = new ArrayList<>();
            for (String col : side) {
                BDailyTrainSeat seat = byCol.get(col);
                if (seat == null) {          // 该列不存在或已售，连续段断开
                    run.clear();
                    continue;
                }
                run.add(seat);
                if (run.size() >= count && (best == null || run.size() - count < best.size() - count)) {
                    best = new ArrayList<>(run.subList(run.size() - count, run.size()));
                }
            }
        }
        return best;
    }
}
