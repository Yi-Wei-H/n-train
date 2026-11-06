package com.weiyi.member.infra.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 钱包流水（只插不改，balance_before/after 可对账）。
 */
@Data
@TableName("m_wallet_flow")
public class MWalletFlow implements Serializable {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long memberId;
    private String flowNo;
    /** 1 充值 2 支付 3 退款 */
    private Integer flowType;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private String bizNo;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
