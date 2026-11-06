package com.weiyi.business.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalTime;

/** 每日区间余票明细 */
@Data
public class DailyTrainTicketDTO implements Serializable {

    /** 车次留空查询时区分不同列车 */
    private String trainCode;
    private String fromStation;
    private String toStation;
    private LocalTime departureTime;
    private LocalTime arrivalTime;
    private Integer seatType;
    private Integer remaining;
    private BigDecimal price;
}
