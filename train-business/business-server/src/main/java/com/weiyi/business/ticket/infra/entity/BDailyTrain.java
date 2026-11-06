package com.weiyi.business.ticket.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("b_daily_train")
public class BDailyTrain extends BaseTimeEntity {

    private LocalDate rideDate;
    private String trainCode;
    private Integer trainType;
    private String departureStation;
    private String arrivalStation;
    private LocalTime departureTime;
    private LocalTime arrivalTime;
    /** 0 未发车 1 运行中 2 已到达 */
    private Integer status;
}
