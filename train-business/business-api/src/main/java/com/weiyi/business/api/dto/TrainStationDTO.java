package com.weiyi.business.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalTime;

/** 车次经停站（管理端按车次整体维护） */
@Data
public class TrainStationDTO implements Serializable {

    private Long id;
    private Long trainId;
    private String stationCode;
    private String stationName;
    private Integer sort;
    private LocalTime departureTime;
    private LocalTime arrivalTime;
    private Integer distanceKm;
}
