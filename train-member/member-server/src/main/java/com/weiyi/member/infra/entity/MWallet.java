package com.weiyi.member.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("m_wallet")
public class MWallet extends BaseTimeEntity {

    private Long memberId;
    private BigDecimal balance;

    @Version
    private Integer version;

    /** 1 正常 0 冻结 */
    private Integer status;
}
