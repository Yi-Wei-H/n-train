package com.weiyi.business.ticket.service;

import java.util.List;
import java.util.Map;

/**
 * 席别 → 座位列布局（每排列号），模板座位生成共用。
 */
public final class SeatLayouts {

    private SeatLayouts() {
    }

    public static final Map<Integer, List<String>> COL_LAYOUT = Map.of(
            1, List.of("A", "C", "F"),                 // 商务座
            2, List.of("A", "C", "D", "F"),            // 一等座
            3, List.of("A", "B", "C", "D", "F"),       // 二等座
            4, List.of("A", "B", "C", "D", "E", "F"),  // 硬卧
            5, List.of("A", "B", "C", "D", "E", "F")   // 硬座
    );

    public static List<String> colsOf(int seatType) {
        return COL_LAYOUT.getOrDefault(seatType, COL_LAYOUT.get(3));
    }
}
