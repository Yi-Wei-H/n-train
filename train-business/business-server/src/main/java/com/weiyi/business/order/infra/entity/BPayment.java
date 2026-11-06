package com.weiyi.business.order.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("b_payment")
public class BPayment extends BaseTimeEntity {

    /** 幂等键 */
    private String payNo;
    private Long orderId;
    private Long memberId;
    private BigDecimal amount;
    /** 1 钱包余额 */
    private Integer payType;
    /** 0 处理中 1 成功 2 失败 */
    private Integer payStatus;
    private LocalDateTime payAt;
}
