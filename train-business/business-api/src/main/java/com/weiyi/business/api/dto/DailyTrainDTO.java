package com.weiyi.business.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** 每日车次（管理端列表） */
@Data
public class DailyTrainDTO implements Serializable {

    private Long id;
    private LocalDate rideDate;
    private String trainCode;
    private Integer trainType;
    private String departureStation;
    private String arrivalStation;
    private LocalTime departureTime;
    private LocalTime arrivalTime;
    private Integer status;
}
