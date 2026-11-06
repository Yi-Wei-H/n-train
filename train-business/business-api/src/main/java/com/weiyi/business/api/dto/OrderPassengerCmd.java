package com.weiyi.business.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/** 下单乘车人快照（member 校验归属后传入，订单域不再回查会员库） */
@Data
public class OrderPassengerCmd implements Serializable {

    @NotNull(message = "乘车人ID不能为空")
    private Long passengerId;

    @NotBlank(message = "乘车人姓名不能为空")
    private String name;

    @NotBlank(message = "证件号不能为空")
    private String idCardNo;

    /** 1成人 2儿童 3学生 */
    @NotNull(message = "票种不能为空")
    private Integer ticketType;
}
