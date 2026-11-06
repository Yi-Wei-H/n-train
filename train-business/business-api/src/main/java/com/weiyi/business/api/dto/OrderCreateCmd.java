package com.weiyi.business.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

/** 下单命令：乘车人快照由 member 校验归属后填入 */
@Data
public class OrderCreateCmd implements Serializable {

    @NotNull(message = "会员ID不能为空")
    private Long memberId;

    @NotNull(message = "乘车日期不能为空")
    private LocalDate rideDate;

    @NotBlank(message = "车次不能为空")
    private String trainCode;

    @NotBlank(message = "出发站不能为空")
    private String fromStation;

    @NotBlank(message = "到达站不能为空")
    private String toStation;

    @NotNull(message = "席别不能为空")
    private Integer seatType;

    @NotEmpty(message = "请选择乘车人")
    private List<OrderPassengerCmd> passengers;

    /** 用户自选座（抽象列位，如 "C1"/"D2"：列字母 + 第 1/2 排相对位）；null/空 = 系统自动连座分配 */
    private List<String> chosenSeats;
}
