package com.weiyi.member.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * AI 代下单命令（服务间 RPC 契约）：member 校验乘车人归属后走既有下单编排。
 * 幂等与金额复核字段由 AI 工具层写操作协议要求，member 侧按幂等键去重、按期望金额复核。
 */
@Data
public class OrderBizCreateCmd implements Serializable {

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
    private List<Long> passengerIds;

    /** 幂等键（AI 工具层 operationId）：同键重复请求返回原结果，不重复下单 */
    @NotBlank(message = "幂等键不能为空")
    private String idempotencyKey;

    /** 期望应付总额：与 member 实时计算不一致时拒绝执行 */
    @NotNull(message = "期望金额不能为空")
    @Positive(message = "期望金额必须为正")
    private BigDecimal expectedAmount;
}
