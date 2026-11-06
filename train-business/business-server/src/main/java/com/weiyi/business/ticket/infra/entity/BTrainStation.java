package com.weiyi.business.ticket.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("b_train_station")
public class BTrainStation extends BaseTimeEntity {

    private Long trainId;
    private Long stationId;
    /** 经停顺序，1 为始发 */
    private Integer sort;
    private LocalTime departureTime;
    private LocalTime arrivalTime;
    private Integer distanceKm;
}
