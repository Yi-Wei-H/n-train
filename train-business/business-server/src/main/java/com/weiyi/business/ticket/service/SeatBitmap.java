package com.weiyi.business.ticket.service;

import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;

/**
 * 座位售出位图工具：sell 串长度 = 经停站数，下标 i 代表第 i 站（0 起）。
 * 订 [from, to) 区间即把该范围字符置 1；退票清 0。
 * 并发安全由数据端"日期+车次"分布式锁保证（§4 原则 2）。
 */
public final class SeatBitmap {

    private SeatBitmap() {
    }

    public static boolean isFree(String sell, int from, int to) {
        requireRange(sell, from, to);
        for (int i = from; i < to; i++) {
            if (sell.charAt(i) == '1') {
                return false;
            }
        }
        return true;
    }

    public static String mark(String sell, int from, int to) {
        requireRange(sell, from, to);
        char[] chars = sell.toCharArray();
        for (int i = from; i < to; i++) {
            chars[i] = '1';
        }
        return new String(chars);
    }

    public static String clear(String sell, int from, int to) {
        requireRange(sell, from, to);
        char[] chars = sell.toCharArray();
        for (int i = from; i < to; i++) {
            chars[i] = '0';
        }
        return new String(chars);
    }

    private static void requireRange(String sell, int from, int to) {
        if (sell == null || sell.length() == 0 || from < 0 || to > sell.length() || from >= to) {
            throw new BusinessException(ErrorCode.SEAT_ALLOC_FAIL, "座位区间非法：" + from + "~" + to);
        }
    }
}
