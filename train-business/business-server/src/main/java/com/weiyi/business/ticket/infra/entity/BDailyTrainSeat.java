package com.weiyi.business.ticket.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("b_daily_train_seat")
public class BDailyTrainSeat extends BaseTimeEntity {

    private LocalDate rideDate;
    private String trainCode;
    private Integer carriageNo;
    private Integer rowNo;
    private String colNo;
    private Integer seatType;
    /** 站序售出位图，0 空闲 1 售出 */
    private String sell;
}
