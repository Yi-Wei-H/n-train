package com.weiyi.business.api.dto;

import lombok.Data;

import java.io.Serializable;

/** 每日座位（含站序售票位图：0 空闲 1 售出，下标即站序） */
@Data
public class DailyTrainSeatDTO implements Serializable {

    private Integer carriageNo;
    private Integer rowNo;
    private String colNo;
    private Integer seatType;
    private String sell;
}
