package com.weiyi.business.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** 余票查询结果行（按席别一条） */
@Data
public class TicketDTO implements Serializable {

    private LocalDate rideDate;
    private String trainCode;
    /** 1 高铁 2 动车 3 普快 4 直达 */
    private Integer trainType;
    private String fromStation;
    private String fromName;
    private String toStation;
    private String toName;
    private LocalTime departureTime;
    private LocalTime arrivalTime;
    /** 历时（分钟） */
    private Long durationMinutes;
    private Integer seatType;
    private Integer remaining;
    private BigDecimal price;
}
