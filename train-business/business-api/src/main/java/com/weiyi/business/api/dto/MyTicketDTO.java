package com.weiyi.business.api.dto;

import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.annotation.JsonSerialize;
import com.weiyi.common.core.util.AesDeserializer;
import com.weiyi.common.core.util.AesSerializer;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 我的车票（姓名/证件传输层 AES 加密；memberId 供前端区分"他人代购"） */
@Data
public class MyTicketDTO implements Serializable {

    private Long id;
    private Long orderId;
    private String orderNo;
    private Long orderItemId;
    /** 购票人（下单人）；不等于当前用户时即"他人代购" */
    private Long memberId;
    private Long passengerId;

    @JsonDeserialize(using = AesDeserializer.class)
    @JsonSerialize(using = AesSerializer.class)
    private String passengerName;

    @JsonDeserialize(using = AesDeserializer.class)
    @JsonSerialize(using = AesSerializer.class)
    private String idCardNo;

    private LocalDate rideDate;
    private String trainCode;
    private String fromStation;
    private String toStation;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private Integer seatType;
    private Integer carriageNo;
    private String seatNo;
    private Integer ticketStatus;
    private BigDecimal price;
}
