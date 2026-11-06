package com.weiyi.business.api.dto;

import lombok.Data;

import java.io.Serializable;

/** 座位明细（车厢号 + 排号 + 列号；seatNo = rowNo + colNo 展示串） */
@Data
public class SeatDTO implements Serializable {

    private Integer carriageNo;

    private Integer rowNo;

    private String colNo;

    private String seatNo;
}
