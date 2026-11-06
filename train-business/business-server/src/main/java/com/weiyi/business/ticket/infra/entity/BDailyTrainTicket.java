package com.weiyi.business.ticket.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("b_daily_train_ticket")
public class BDailyTrainTicket extends BaseTimeEntity {

    private LocalDate rideDate;
    private String trainCode;
    private String fromStation;
    private String toStation;
    private LocalTime departureTime;
    private LocalTime arrivalTime;
    private Integer seatType;
    private Integer remaining;
    private BigDecimal price;
}
