package com.weiyi.member.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * AI 代退票命令（服务间 RPC 契约）：明细级退款，member 校验归属与可退后走既有退款编排。
 */
@Data
public class OrderBizRefundCmd implements Serializable {

    @NotNull(message = "会员ID不能为空")
    private Long memberId;

    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @NotEmpty(message = "请选择要退的票")
    private List<Long> itemIds;

    /** 幂等键（AI 工具层 operationId）：同键重复请求返回原结果，不重复退款 */
    @NotNull(message = "幂等键不能为空")
    private String idempotencyKey;

    /** 期望应退总额（预检时可计算时给值）：不一致时拒绝执行 */
    @Positive(message = "期望金额必须为正")
    private BigDecimal expectedRefundAmount;
}
