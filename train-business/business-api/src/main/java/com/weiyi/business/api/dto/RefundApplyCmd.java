package com.weiyi.business.api.dto;

import tools.jackson.databind.annotation.JsonDeserialize;
import com.weiyi.common.core.util.AesDeserializer;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 退款申请（明细级，支持部分退） */
@Data
public class RefundApplyCmd implements Serializable {

    @NotNull(message = "会员ID不能为空")
    private Long memberId;

    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @NotEmpty(message = "请选择要退的票")
    private List<Long> itemIds;

    /** 操作者证件号（可空）：操作者非下单人时必填，用于"乘车人本人退他人代购票"的证件号比对；
     *  传输层 AES 密文（ENC: 前缀）自动解密，明文原样兼容 */
    @JsonDeserialize(using = AesDeserializer.class)
    private String operatorIdCard;
}
