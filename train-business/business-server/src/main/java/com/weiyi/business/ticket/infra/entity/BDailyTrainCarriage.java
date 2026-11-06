package com.weiyi.business.ticket.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("b_daily_train_carriage")
public class BDailyTrainCarriage extends BaseTimeEntity {

    private LocalDate rideDate;
    private String trainCode;
    private Integer carriageNo;
    private Integer seatType;
    private Integer seatCount;
    private Integer rowCount;
}
