package com.weiyi.member.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/** 下单命令（频次触发验证码后必传 captchaKey/captchaCode） */
@Data
public class OrderCreateWebCmd implements Serializable {

    @NotNull(message = "乘车日期不能为空")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate rideDate;

    @NotNull(message = "车次不能为空")
    private String trainCode;

    @NotNull(message = "出发站不能为空")
    private String fromStation;

    @NotNull(message = "到达站不能为空")
    private String toStation;

    @NotNull(message = "席别不能为空")
    private Integer seatType;

    @NotEmpty(message = "请选择乘车人")
    private List<Long> passengerIds;

    /** 自选座位（抽象列位，如 "C1"/"D2"；空=系统连座优先分配） */
    private List<String> chosenSeats;

    /** 触发验证码后必传 */
    private String captchaKey;

    private String captchaCode;
}
