package com.weiyi.business.ticket.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 票价计算（演示级简化）：price = 公里数 × 车次每公里单价 × 席别系数，最低 5 元。
 */
public final class PriceUtil {

    private PriceUtil() {
    }

    /** 每公里基准价（元）：高铁/动车/普快/直达 */
    private static final BigDecimal[] PER_KM = {
            BigDecimal.ZERO, BigDecimal.valueOf(0.46), BigDecimal.valueOf(0.31),
            BigDecimal.valueOf(0.15), BigDecimal.valueOf(0.20)
    };

    /** 席别系数：商务/一等/二等/硬卧/硬座 */
    private static final BigDecimal[] SEAT_FACTOR = {
            BigDecimal.ONE, BigDecimal.valueOf(2.6), BigDecimal.valueOf(1.6),
            BigDecimal.ONE, BigDecimal.valueOf(0.9), BigDecimal.valueOf(0.45)
    };

    public static BigDecimal compute(int distanceKm, int trainType, int seatType) {
        BigDecimal perKm = trainType >= 1 && trainType <= 4 ? PER_KM[trainType] : PER_KM[3];
        BigDecimal factor = seatType >= 1 && seatType <= 5 ? SEAT_FACTOR[seatType] : SEAT_FACTOR[3];
        BigDecimal price = BigDecimal.valueOf(distanceKm).multiply(perKm).multiply(factor);
        if (price.compareTo(BigDecimal.valueOf(5)) < 0) {
            price = BigDecimal.valueOf(5);
        }
        return price.setScale(2, RoundingMode.HALF_UP);
    }
}
