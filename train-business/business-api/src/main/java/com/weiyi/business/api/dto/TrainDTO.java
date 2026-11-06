package com.weiyi.business.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 车次（含车厢模板，管理端维护） */
@Data
public class TrainDTO implements Serializable {

    private Long id;
    private String code;
    private Integer trainType;
    private List<CarriageDTO> carriages;
}
