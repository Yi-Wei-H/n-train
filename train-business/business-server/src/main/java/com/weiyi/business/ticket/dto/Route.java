package com.weiyi.business.ticket.dto;

import com.weiyi.common.core.exception.BusinessException;
import com.weiyi.common.core.exception.ErrorCode;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 车次线路：车次号 → 按序站表（编码、时刻、公里数），下单/生成每日车次/过站巡检共用。
 * 站表时刻为 LocalTime，站序中时刻较上一站倒退即跨天（dayOffset +1），折算绝对时间由调用方传入乘车日。
 *
 * @param codes 按经停顺序排列的车站电报码（下标即站序）
 * @param stops 按序站时刻（与 codes 同下标）
 */
public record Route(Long trainId, Integer trainType, List<String> codes, List<StopTime> stops) {

    /** 按序站时刻：电报码 + 发/到 LocalTime + 跨天偏移（终站无发车、首站可无到达） */
    public record StopTime(String code, LocalTime depart, LocalTime arrive, int dayOffset) {
    }

    /** 站序 */
    public int indexOf(String stationCode) {
        int idx = codes.indexOf(stationCode);
        if (idx < 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "车次不经停车站：" + stationCode);
        }
        return idx;
    }

    /** 乘客区间出发时刻：上车站发车时间 + 乘车日跨天折算 */
    public LocalDateTime departureAt(LocalDate rideDate, String fromCode) {
        StopTime stop = stops.get(indexOf(fromCode));
        if (stop.depart() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "上车站无发车时刻：" + fromCode);
        }
        return rideDate.plusDays(stop.dayOffset()).atTime(stop.depart());
    }

    /** 乘客区间到达时刻：下车站到达时间 + 乘车日跨天折算 */
    public LocalDateTime arrivalAt(LocalDate rideDate, String toCode) {
        StopTime stop = stops.get(indexOf(toCode));
        if (stop.arrive() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "下车站无到达时刻：" + toCode);
        }
        return rideDate.plusDays(stop.dayOffset()).atTime(stop.arrive());
    }
}
