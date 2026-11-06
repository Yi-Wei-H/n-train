package com.weiyi.business.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;

/** 每日车次生成结果 */
@Data
public class GenerateResultDTO implements Serializable {

    private LocalDate rideDate;
    private int trainCount;
    private int carriageCount;
    private int seatCount;
    private int ticketCount;

    /** 累加单趟车次的生成量 */
    public void accumulate(GenerateResultDTO other) {
        this.trainCount += other.trainCount;
        this.carriageCount += other.carriageCount;
        this.seatCount += other.seatCount;
        this.ticketCount += other.ticketCount;
    }
}
