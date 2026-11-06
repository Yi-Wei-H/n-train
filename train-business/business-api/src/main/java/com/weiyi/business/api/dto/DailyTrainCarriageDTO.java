package com.weiyi.business.api.dto;

import lombok.Data;

import java.io.Serializable;

/** 每日车厢（座位查询页的车厢下拉） */
@Data
public class DailyTrainCarriageDTO implements Serializable {

    /** 车次留空查询时，下拉需要车次号区分不同列车的车厢 */
    private String trainCode;
    private Integer carriageNo;
    private Integer seatType;
    private Integer seatCount;
    private Integer rowCount;
}
