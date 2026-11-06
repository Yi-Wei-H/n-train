package com.weiyi.member.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 订单明细（服务间 RPC 契约，仅携带 AI 工具与展示所需字段，证件号不入契约）。
 */
@Data
public class MemberOrderItemDTO implements Serializable {

    private Long orderItemId;

    private Long passengerId;

    private String passengerName;

    /** 1 成人 2 儿童 3 学生 */
    private Integer passengerType;

    private Integer seatType;

    private String seatTypeName;

    private String carriageNo;

    private String seatNo;

    private BigDecimal price;

    /** 明细状态码（业务域定义） */
    private Integer itemStatus;

    private String itemStatusName;

    /** 当前是否可退 */
    private Boolean refundable;

    /** 预计可退金额（可计算时给值，含手续费扣减；不可计算为空） */
    private BigDecimal refundAmount;
}
