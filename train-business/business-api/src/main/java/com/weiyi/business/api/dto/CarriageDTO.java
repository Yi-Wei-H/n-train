package com.weiyi.business.api.dto;

import lombok.Data;

import java.io.Serializable;

/** 车厢模板 */
@Data
public class CarriageDTO implements Serializable {

    private Long id;
    private Integer carriageNo;
    private Integer seatType;
    private Integer seatCount;
    private Integer rowCount;
}
